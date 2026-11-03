# ADR-0001: Modular monolith, package-by-feature

**Status:** Accepted · 2026-10-30

## Context
CloudForge has about ten business capabilities that share one tightly coupled workflow (request → approval → provisioning). One team builds it, and the load is modest. Strong consistency between approval and job creation matters more than independent scaling.

## Decision
Ship one Spring Boot application. Organize the code by business capability under `com.cloudforge.<module>`. Modules call each other only through application services or in-process events, never through another module's repositories. Asynchronous work runs in-process, backed by PostgreSQL tables. No Kafka, Redis or service mesh.

## Consequences
- One transaction can span request, approval, job and audit. There are no sagas.
- One build, one deploy, one database to operate.
- Boundaries are kept by convention and review. If they erode, add Spring Modulith or ArchUnit verification tests (cheap, test-scope only).
- Extracting a module later (for example the provisioning worker) is possible because it already talks through narrow interfaces and DB-backed jobs.
