# Deployment architecture decision

**Decision (2026-11-23):** run CloudForge on **AWS ECS Fargate**, deployed by **GitHub Actions**
(build once, promote the same image staging → production with an approval gate), with **RDS
PostgreSQL** and **CloudFront + WAF** as the only public entry point. No Kubernetes, no GitOps
controller, no separate CD product. Infrastructure: [`infra/`](../../infra/README.md).

The reasoning follows the decision framework of the CD study *Continuous Delivery in Startups,
2024–2026* (section R): start from the runtime and the operational problem, and add CD complexity
only when it removes more work than it creates and someone owns it.

## The framework applied

| Question | CloudForge | Consequence |
|---|---|---|
| 1. Deploy onto clients' infrastructure? | No, one product | The problem is release safety, not client isolation |
| 2. Existing runtime? | None; two container images and an AWS account | Choose the most managed runtime that fits |
| 3. Apps and environments? | 2 apps, 2 environments | No templates or extra systems needed |
| 4. Kubernetes clusters, independent teams? | 0 clusters, 1 developer | No Kubernetes, no GitOps (study: "rarely" at this size) |
| 5. Who owns delivery, how much of their week? | One person, well under 0.5 FTE | No self-hosted control plane |
| 6. Does a bad release cost money within minutes? | Not yet | No canary or service mesh; feature flags if needed later |
| 7. Does anyone outside approve releases? | Audit trail matters (governance product) | GitHub environment with a required reviewer for production |

## Why ECS Fargate specifically

- **The backend calls AWS APIs to provision resources.**
  - On ECS it has an IAM task role: credentials that rotate on their own and are never stored.
  - A third-party PaaS would need long-lived AWS keys in its settings. That is the credential exposure the study's security section identifies as the main 2025–26 risk.
- **The images already exist.** CI builds, scans and smoke-tests the production images; ECS runs exactly those.
- **Rollback is built in.**
  - Images are tagged with the immutable commit SHA.
  - The ECS deployment circuit breaker rolls back failed deployments automatically.
- **No control plane to operate.** There's no cluster version support window and no controller upgrades. ECS has no control-plane charge.

## Rejected alternatives

| Option | Why not now | Revisit when |
|---|---|---|
| EKS + Argo CD | About 6× the cost and operating work for 2 services; one owner | Workloads need the Kubernetes ecosystem, or several clusters and teams |
| Dedicated CD platform (Octopus, Harness) | Duplicates GitHub environments (approvals, history) | Mixed deployment targets or an audit requirement CI can't meet |
| Canary / blue-green / service mesh | Nothing to measure without real traffic | A revenue path where a bad release costs money in minutes |
| Kamal on one VM | Cheapest, but OS patching becomes ours and the task role is lost | Steady load where cost matters more than managed operations |
| Third-party PaaS (Render, Railway) | Needs stored AWS keys for provisioning | Never, while the backend provisions AWS resources |

## Rules that come with it

- **Build once, promote:** production deploys the image staging tested, never a rebuild.
- **Expand/contract database migrations:**
  - Never drop or rename a column in the same release that stops using it.
  - Otherwise rolling back the application meets a schema it no longer understands.
- **Infrastructure changes go through Terraform**, separately from application deployments. They have different speeds and different risks.
