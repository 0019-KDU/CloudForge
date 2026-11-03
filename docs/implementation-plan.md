# Implementation plan

Each phase ends with: compile, tests run (`mvn verify`), code review, docs updated, report.

| # | Phase | Deliverables | Depends on | Status |
|---|---|---|---|---|
| 1 | Foundation and configuration | Package root `com.cloudforge`, YAML profiles, env-only credentials, actuator, graceful shutdown, UTC clock, local compose DB | – | ✅ Done |
| 2 | Common infrastructure | Problem Details error model, domain exception hierarchy, correlation-ID filter, security baseline (deny-by-default, CSRF, JSON 401/403) | 1 | ✅ Done |
| 3 | Database and Flyway | Flyway baseline, `ddl-auto=validate`, Hikari tuning, Testcontainers support, failsafe IT split | 1 | ✅ Done |
| 4 | Identity and organization | `app_user`, `role`, `user_role`, `department`, `team`, `invitation` migrations and entities, base entity (UUIDv7, timestamps, version), admin API for org data, users, invitations | 3 | ✅ Done |
| 5 | Authentication | JWT access tokens + rotating refresh tokens (ADR-0003), login/refresh/logout/me, password policy, login throttling, invitation registration, first-admin bootstrap, self-lockout guard, `invited_by` + actor tracking | 4 | ✅ Done |
| 6 | Resource catalog | `resource_type`, `resource_policy`, per-type spec validators (AWS/GCP/K8s), admin endpoints, OpenAPI (springdoc) | 5 | ✅ Done |
| 7 | Requests and state machine | `resource_request`, request numbers, transitions, `authorization` access policies (owner / approver / admin / auditor), scoped list queries, `maxActivePerUser` quota | 6 | ✅ Done |
| 8 | Single-manager approval | Approve/reject use cases, `approval_record`, concurrency guards | 7 | ✅ Done |
| 9 | Audit | `audit_event`, in-transaction event recording, audit query API for auditor/owner | 7–8 | ✅ Done |
| 10 | Provisioning engine | Job/attempt/result tables, worker with `SKIP LOCKED` leases, retry policy, mock provider, feature flags, admin retry | 8–9 | ✅ Done |
| 11 | Kubernetes adapter | Official Kubernetes Java client; namespace, quota and RBAC; mock-server tests | 10 | ✅ Done |
| 12 | GCP adapter | Resource Manager v3 client; long-running operation polling | 10 | ✅ Done |
| 13 | AWS adapter | AWS SDK v2 Organizations; CreateAccount status polling | 10 | ✅ Done |
| 14 | Notifications | In-app notifications from domain events (after commit) | 8, 10 | ✅ Done |
| 15 | Hardening | Security test sweep, concurrency tests, metrics, docs pass | all | ✅ Done |

**First milestone (done):** phases 1–3. The application boots against real PostgreSQL, applies migrations, exposes health, rejects everything else with a consistent error contract, and has the test harness in place for every later phase.

**All phases are complete.** Verification: `./mvnw verify` with JDK 21 and Docker runs the unit and web-slice tests (surefire) and the Testcontainers integration tests (failsafe).

## Follow-up hardening (done)
- Log redaction of unexpected exceptions (`common.logging.Redaction`).
- Per-client login and registration rate limit (`LoginThrottle`, V9).
- `ETag`/`If-Match` lost-update protection for admin edits.
- Daily data-retention job: expired refresh tokens, old read notifications, stale rate-limit windows (`DataRetentionJob`).

## Frontend support (done)
- User directory with team and department (G1, G2); dashboard summary counts for requests and provisioning jobs (G9); policy limits in the public catalog (G3); request text search and multi-status filter (G4, G5); provisioning job list (G6).
- Profile edit, password change, admin-issued password reset ("forgot password" without email), draft deletion.

## Open (needs a decision or external access)
- **Real-cloud verification** of the AWS, GCP and Kubernetes adapters in sandbox accounts. Needs credentials and explicit authorization (creates billable resources).
- **Requester access inside provisioned resources** (for example a namespace RoleBinding) needs a mapping from CloudForge users to cloud identities.
- **Resource decommissioning** is a new business process (who requests it, whether it needs approval, data handling).
- **Per-request account check** to close the 10-minute JWT read window (one indexed lookup per request; trade-off with statelessness, see ADR-0003).
- **Email or chat notifications** need a transactional outbox and SMTP or webhook details. With email, password reset could become fully self-service (today an administrator issues the token).
- Frontend, deployment, CI/CD and hosting infrastructure are separate tasks.
