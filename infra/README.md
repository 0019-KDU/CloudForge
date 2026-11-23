# CloudForge infrastructure (Terraform)

AWS infrastructure for running CloudForge: ECS Fargate behind an internal load balancer, reached only
through CloudFront, with PostgreSQL on RDS. Why this shape: [docs/operations/deployment-architecture.md](../docs/operations/deployment-architecture.md).

```
Browser ─HTTPS─► CloudFront + WAF ─VPC origin─► internal ALB ─┬─ /api/* ─► backend  (Fargate)
                                                               └─ other ──► frontend (Fargate)
                                                     backend ─► RDS PostgreSQL (private subnets)
```

## Layout

| Folder | What | State |
|---|---|---|
| `bootstrap/` | S3 bucket + KMS key for Terraform state | local (run once per account) |
| `global/` | ECR repositories, GitHub OIDC provider | S3 `global/` |
| `envs/staging/`, `envs/production/` | One environment each, same module, different settings | S3 `envs/<env>/` |
| `modules/network` | VPC, 2 AZs, public + private subnets, no NAT gateway | |
| `modules/database` | RDS PostgreSQL 17, TLS enforced, password in Secrets Manager | |
| `modules/app` | CloudFront + WAF, VPC origin, internal ALB, ECS cluster, services, logs, secrets, roles | |
| `modules/github-deploy-role` | IAM role GitHub Actions assumes through OIDC, per environment | |
| `modules/environment` | Wires the modules above into one environment | |

## Security decisions

- **No long-lived credentials anywhere.**
  - GitHub Actions deploys through OIDC, trusted only for this repository *and* the matching GitHub environment.
  - The backend uses its ECS task role.
- **Secrets never enter Terraform state.** The database password, JWT key and first-admin password are ephemeral values, written through write-only attributes into Secrets Manager. ECS injects them at start-up.
- **Only CloudFront is public.**
  - The load balancer is internal, in the private subnets, reached through a CloudFront VPC origin.
  - The database has no internet route.
  - Task security groups accept the load balancer only.
- **WAF at the edge:**
  - AWS managed rules (IP reputation, known bad inputs, common attacks)
  - a per-IP rate limit on `/api/auth/*`, which stops password guessing
  - a general per-IP rate limit
- **Hardened containers:** read-only root filesystem, all Linux capabilities dropped, non-root users.
- **Immutable images:** ECR tags can't be overwritten, so a commit SHA always means the same tested image.
- **Recoverable state:** the state bucket is versioned, KMS-encrypted with rotation, TLS-only and locked with S3-native locking.

Accepted findings, each with a reason and an expiry date, are in [`.trivyignore.yaml`](../.trivyignore.yaml): HTTP between CloudFront and the internal ALB, and HTTPS egress from the tasks.

## Cost (approximate, ap-south-1, per month)

| | Staging | Production |
|---|---|---|
| Fargate (backend 0.5 vCPU/1 GB, frontend 0.25 vCPU/0.5 GB) | ~$10 on Spot, 1 each | ~$60, 2 each |
| RDS PostgreSQL | ~$15 (`db.t4g.micro`) | ~$30 (`db.t4g.small`) |
| Internal ALB | ~$20 | ~$20 |
| WAF (web ACL + 5 rules + requests) | ~$10 | ~$10 |
| CloudFront, Secrets Manager, CloudWatch, ECR | ~$5 | ~$10 |
| **Total** | **~$60** | **~$130** |

Shared once per account: state KMS key ~$1. No NAT gateway, no VPC endpoints.

## Prerequisites

- **Terraform** ≥ 1.11 (write-only attributes). Check with `terraform version`.
- **AWS CLI v2**, signed in to the target account with an administrator profile. Use `aws sso login` or `aws configure`; never commit keys.
- **Docker**, for the first image push.

## First-time setup, step by step

All commands run from `infra/`.

**1. State bucket** (once per account)
```bash
cd bootstrap
terraform init
terraform apply
terraform output        # note state_bucket and state_kms_key_arn
cd ..
```

**2. Shared resources**
```bash
cd global
cp backend.hcl.example backend.hcl            # fill in bucket, region, kms_key_id from step 1
terraform init -backend-config=backend.hcl
terraform apply
terraform output repository_urls
cd ..
```

**3. First images.** An environment's first apply needs images that already exist in ECR. Push the current `main` commit once:
```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-south-1
REGISTRY=$ACCOUNT.dkr.ecr.$REGION.amazonaws.com
TAG=$(git rev-parse HEAD)

aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REGISTRY
docker build -t $REGISTRY/cloudforge-backend:$TAG  ../backend
docker build -t $REGISTRY/cloudforge-frontend:$TAG ../frontend
docker push $REGISTRY/cloudforge-backend:$TAG
docker push $REGISTRY/cloudforge-frontend:$TAG
echo $TAG                                      # use as image_tag in step 4
```
From then on the CD workflow pushes images.

**4. Staging**
```bash
cd envs/staging
cp backend.hcl.example backend.hcl            # same values as step 2
cp terraform.tfvars.example terraform.tfvars  # image_tag from step 3, your admin e-mail
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan                     # read it
terraform apply tfplan
terraform output
```
- **Timing:** the first apply takes about 20–30 minutes. The CloudFront VPC origin and distribution take most of it.
- **First sign-in:**
  1. Open the `url` output.
  2. Sign in as the bootstrap admin. The password is in Secrets Manager under `bootstrap_admin_secret_name`.
  3. Remove `bootstrap_admin_email` from `terraform.tfvars` and apply again.

**5. Production:** the same as step 4, in `envs/production`. Apply it only when you need it, because it costs money.

**6. GitHub:** create GitHub environments `staging` and `production` (Settings → Environments).
- In each, add the variable `AWS_DEPLOY_ROLE_ARN` with that environment's `deploy_role_arn` output. It's a variable, not a secret, because an ARN isn't secret.
- In `production`, add yourself as a **required reviewer**.

## Day-2 operations

| Task | How |
|---|---|
| Deploy a new version | The CD workflow does it: it registers a new task definition revision with the new image and updates the service. Terraform ignores which revision a service runs. |
| Change CPU, memory or environment variables | Edit Terraform and apply. That registers a new revision; the next deployment rolls it out. |
| Roll back | Redeploy an earlier commit SHA. ECS also rolls back by itself when new tasks fail their health checks (deployment circuit breaker). |
| Rotate the DB password | Increase `password_version` in `modules/database`, apply, then redeploy the backend. |
| Rotate the JWT key | Increase `jwt_secret_version`, apply, redeploy. Everyone is signed out. |
| Enable real AWS provisioning | Attach the needed policy to the role named by the `backend_task_role_name` output, and set `CLOUDFORGE_PROVISIONING_AWS_MODE`. |
| Custom domain | Issue an ACM certificate in **us-east-1**, then set `domain_name` and `acm_certificate_arn` and apply. Point DNS at `cloudfront_domain_name`. |
| Tear down staging | `terraform destroy` in `envs/staging` (staging has no deletion protection). |

## Validating changes locally

```bash
terraform fmt -recursive -check
cd envs/staging && terraform init -backend=false && terraform validate
```
CI's Trivy job also scans this folder for misconfigurations on every pull request.
