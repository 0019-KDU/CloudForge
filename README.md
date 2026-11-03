# CloudForge
A platform for requesting, approving, tracking and provisioning cloud resources across AWS, GCP and Kubernetes.

## Backend quick start

Requirements: JDK 21, Docker (for the local database and integration tests).

```bash
cd backend
cp .env.example .env            # set CLOUDFORGE_DB_PASSWORD and CLOUDFORGE_JWT_SECRET
                                # optional first start: CLOUDFORGE_BOOTSTRAP_ADMIN_EMAIL / _PASSWORD
docker compose up -d            # local PostgreSQL 17
# load .env into your shell, then:
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

With the `local` profile the provisioning worker runs against **simulated** providers, so the full
request → approve → provision flow works without any cloud account.

Or run `TestCloudForgeApplication` (in test sources) to start against a throwaway Testcontainers database.

API docs (local profile): http://localhost:8080/swagger-ui.html

### Enabling real provisioning (deployed environments only)
Set `CLOUDFORGE_PROVISIONING_WORKER_ENABLED=true` and, per provider,
`CLOUDFORGE_PROVISIONING_{AWS,GCP,KUBERNETES}_MODE=real`. Credentials come from the platform
(IAM role, Workload Identity, service account), never from configuration. Read
[provisioning-design.md](docs/architecture/provisioning-design.md#safety) first.

```bash
./mvnw test     # unit and web-slice tests (no Docker needed)
./mvnw verify   # plus *IT integration tests against PostgreSQL via Testcontainers
```

Health: `GET http://localhost:8080/actuator/health`

## Frontend quick start

Requirements: Node.js 22 (≥ 22.12). With the backend running on port 8080:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api to the backend
npm run check      # typecheck, lint, tests and production build
```

Details: [frontend setup](docs/frontend/frontend-setup.md).

## Documentation
- [Architecture overview](docs/architecture/overview.md)
- [Implementation plan](docs/implementation-plan.md)
- [Decisions (ADRs)](docs/decisions/README.md)
- [CI architecture](docs/ci-cd/CI-ARCHITECTURE.md): pull request checks, security scanning, required status check
- Frontend: [architecture](docs/frontend/architecture.md) · [API integration](docs/frontend/api-integration.md) · [design system](docs/frontend/design-system.md) · [plan](docs/frontend/implementation-plan.md)
