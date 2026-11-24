# CloudForge CD architecture

How a merged commit reaches staging and production. CI (build, test, scan, images) is described in
[CI-ARCHITECTURE.md](CI-ARCHITECTURE.md). The AWS side is described in [infra/README.md](../../infra/README.md),
and why it looks this way in [deployment-architecture.md](../operations/deployment-architecture.md).

```
merge to main (application or CI change; docs/Terraform-only merges deploy nothing)
   └─► CI run (ci.yml): tests, scans, images built + smoke-tested ─► ci-required ✅
          └─► deploy-staging (calls deploy.yml)
                 push the run's tested images to ECR as :<commit-sha>
                 roll out backend, then frontend; wait for COMPLETED
                 check the public URL (page 200, API 401)

Actions → Deploy → Run workflow (environment: production, image_tag: <sha staging runs>)
   └─► "production" environment approval (required reviewer) ─► same rollout, same images
```

## Principles

| Principle | How |
|---|---|
| **Build once, promote** | Staging gets the images the CI run built and smoke-tested, loaded from the run's artifacts, not rebuilt. Production deploys the same ECR tag. ECR tags are immutable, so a SHA can't be overwritten. |
| **Continuous delivery, not deployment** | Staging is automatic. Production needs one click plus an approval, which the study's evidence (and AWS's definition) recommends at this size. |
| **No stored cloud credentials** | OIDC: the job gets 1-hour credentials for the environment's deploy role. That role trusts only `repo:0019-KDU/CloudForge:environment:<env>`, so a job gets AWS access only after the environment's protection rules pass. |
| **Least privilege** | The deploy role can push or pull two ECR repositories, register task definitions, update its own two services, and pass its two task roles to ECS. Production's role can't push images at all. |
| **Fail safe** | ECS's deployment circuit breaker rolls back by itself if new tasks never become healthy. `ecs-deploy.sh` notices that and fails the run. |
| **One deployment at a time** | Per-environment concurrency group: a second deployment waits and never interrupts a running one. |

## The deployment, step by step (`.github/scripts/ecs-deploy.sh`)

1. **Validate inputs.** The environment name and a full 40-character commit SHA. Manual runs take free text, so nothing reaches a command unchecked.
2. **Images.**
   - On the staging run from CI: `docker load` the tested image tars, then push them to ECR. An existing tag is skipped on a re-run.
   - Then check that both images exist in ECR. Promotion and rollback can only deploy commits CI built on `main`.
3. **For backend, then frontend:**
   1. Read the service's latest task definition; Terraform owns everything except the image.
   2. Register a new revision with only the image changed.
   3. Update the service, then poll until the rollout is `COMPLETED` on that revision.
   - If the service ends up on a different revision, the circuit breaker rolled back, and the run fails with a pointer to the logs.
   - Backend goes first, so a new frontend never calls an older API.
4. **Public check.** When the environment variable `APP_URL` is set: through CloudFront, `/` must answer 200 and `/api/users/me` must answer 401. That proves both routes and that authentication is on.

The job summary lists every service with its deployed SHA.

## Rollback

| Situation | What to do |
|---|---|
| New tasks never become healthy | Nothing: the circuit breaker already rolled back, and the run is red |
| Healthy but wrong (a bug) | Actions → **Deploy** → Run workflow, same environment, the **previous** commit SHA (the job summary of the previous run shows it) |
| Database migration involved | Rolling back the code is only safe if migrations followed expand/contract. Never drop or rename a column in the release that stops using it. |

ECR keeps the 30 most recent images per repository, so about 30 releases can be rolled back to.

## One-time GitHub setup (after the AWS infrastructure exists)

1. **Settings → Environments → New environment `staging`:**
   - Variables:
     - `AWS_DEPLOY_ROLE_ARN`: Terraform output `deploy_role_arn` of `infra/envs/staging`
     - `APP_URL`: Terraform output `url`, without a trailing slash
   - Deployment branches: **Selected branches → `main`**
2. **Environment `production`:** the same variables from `infra/envs/production`, plus:
   - **Required reviewers:** yourself. Keep "prevent self-review" off while you work alone.
   - Deployment branches: **`main`** only.
3. **Settings → Secrets and variables → Actions → Variables (repository):**
   - `AWS_ACCOUNT_ID`: the 12-digit account. The deploy refuses any other account.
   - `AWS_REGION`: `ap-south-1`. Optional, since that's the default.
   - `CD_STAGING_ENABLED`: `true`. Turns on the automatic staging deployment. Until then CI never tries to deploy.

None of these are secrets: role ARNs, URLs and account IDs aren't credentials. No AWS secret is stored in GitHub at all.
