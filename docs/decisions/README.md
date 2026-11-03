# Architecture Decision Records

Format: Context → Decision → Consequences. Status is one of Proposed, Accepted, Superseded.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith, package-by-feature | Accepted |
| [0002](0002-flyway-schema-ownership.md) | Flyway owns the schema; Hibernate validates only | Accepted |
| [0003](0003-jwt-authentication.md) | JWT access tokens with rotating refresh tokens | Accepted |
| [0004](0004-problem-details-error-model.md) | RFC 9457 Problem Details as the single error format | Accepted |
| [0005](0005-postgres-job-queue.md) | PostgreSQL-backed provisioning job queue | Accepted |
| [0006](0006-resource-spec-validation.md) | Resource types in code, specifications as validated JSONB | Accepted |
| [0007](0007-audit-and-notification-delivery.md) | Audit in the business transaction, notifications after commit | Accepted |
| [0008](0008-real-provisioning-opt-in.md) | Real cloud provisioning only on explicit per-provider opt-in | Accepted |
