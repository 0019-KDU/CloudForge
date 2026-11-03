# Frontend ↔ backend integration

> Status: updated after the backend closed gaps G1 and G3–G8 (see section 8). Source of truth: the Spring Boot controllers and DTOs under
> `backend/src/main/java/com/cloudforge/**/api`. Nothing below is assumed; every endpoint, field and
> enum value was read from code. Section 8 lists what the frontend needs but the backend lacks.

## 1. Transport and conventions

| Topic | Backend behaviour | Frontend consequence |
|---|---|---|
| Base path | `/api/**`, JSON | API client base URL `/api` (same origin) |
| CORS | **Not configured** | Vite dev server proxies `/api` → `http://localhost:8080`; deployments serve SPA and API from one origin (reverse proxy). `VITE_API_BASE_URL` stays a relative path by default |
| Auth header | `Authorization: Bearer <accessToken>` | Axios request interceptor adds it from memory |
| CSRF | Disabled (bearer auth; only cookie is `SameSite=Strict`, `Path=/api/auth`) | No CSRF token handling |
| Correlation | Every response has `X-Correlation-Id`; client may send one (`[A-Za-z0-9._-]{8,64}`) | Client sends a UUID per request; error UI shows the ID ("quote this when reporting") |
| Pagination | `?page=0&size=20&sort=field,asc|desc`; `size` ≤ 100; sort fields whitelisted (else `400 UNSUPPORTED_SORT`) | Server-side pagination everywhere; sort only on whitelisted columns |
| Page shape | `{ content: T[], page, size, totalElements, totalPages }` | `PageResponse<T>` type |
| Concurrency | Admin resources return `ETag: "<version>"` and `version`; `If-Match` → `412` on mismatch | Admin mutations send `If-Match: "<version>"` from the loaded entity; 412 → "changed by someone else, reload" |
| Timestamps | ISO-8601 `Instant` (UTC) | Rendered in the browser's local zone, with absolute time on hover |
| Visibility | Requests the caller may not see return **404**, not 403 | A 404 on a request page shows "not found or no access", never a hint that it exists |
| Unknown body fields | Ignored | Never send `status`, `requesterId`, `assignedApproverId` |

## 2. Authentication model

Source: `AuthController`, `AuthenticationService`, `RefreshTokenService`, `SecurityConfig`, ADR-0003.

- **Login** `POST /api/auth/login {email, password}` → `200 {accessToken, tokenType: "Bearer", expiresIn}` (seconds, 600 by default) and `Set-Cookie: cf_refresh=…; HttpOnly; Secure; SameSite=Strict; Path=/api/auth`.
- **Access token**: HS256 JWT, 10 minutes, claim `roles` (e.g. `["MANAGER"]`), `sub` = user id. Kept **in memory only** (module variable), never in `localStorage`/`sessionStorage`.
- **Refresh** `POST /api/auth/refresh` (cookie only, no body) → same body + rotated cookie. **Each refresh token is single-use; presenting a rotated token revokes the whole family** (user is signed out everywhere).
- **Logout** `POST /api/auth/logout` → `204`, clears the cookie. Idempotent.
- **Register** `POST /api/auth/register {invitationToken, displayName, password}` → `201 {id, email, displayName, roles}`. Invitation-only; no tokens are issued, the user signs in afterwards.
- **Current user** `GET /api/users/me` → `UserResponse` read fresh from the DB (roles, manager, team).
- **Throttling**: login and register, 20 attempts / 5 min per client address → `429 TOO_MANY_ATTEMPTS` with `Retry-After` (seconds). Per account: 5 failures → escalating lock, reported as the same `401 INVALID_CREDENTIALS`.
- **Password policy** (register): ≥ 12 characters, ≤ 72 UTF-8 bytes, not a common password, must not contain the email local part (≥ 4 chars). Server returns `422 WEAK_PASSWORD`. The client mirrors length rules only; the blocklist stays server-side.
- **Forgot password** is admin-assisted (there's no email channel): an admin issues a one-time token (`POST /api/admin/users/{id}/password-reset`, valid 24 h, shown once) and the user redeems it at `POST /api/auth/password-reset {token, newPassword}` → `204`. Redeeming clears a lockout and revokes all sessions. Errors: `422 RESET_TOKEN_INVALID`, `WEAK_PASSWORD`; throttled like login.
- **Change password** `POST /api/users/me/password {currentPassword, newPassword}` → `204`. It **revokes every session, including the current one**, so the client clears its state and sends the user to sign in. Errors: `422 INVALID_CURRENT_PASSWORD`, `PASSWORD_UNCHANGED`, `WEAK_PASSWORD`; `429` when throttled.
- **Profile edit** `PUT /api/users/me {displayName ≤100}` → `UserResponse`.

### Required client behaviour (derived from rotation + reuse detection)

1. **Single-flight refresh.** All 401-triggered and proactive refreshes share one in-flight promise. Two parallel refreshes with the same cookie would look like token theft and revoke the session.
2. **Cross-tab serialization.** Refresh runs inside `navigator.locks.request("cf-refresh", …)`, so two tabs never present the same cookie. After the lock is acquired the browser already holds the newest cookie.
3. **Boot** = one refresh attempt (the in-memory token is gone after a reload). Guarded at module level so React StrictMode's double effects cannot fire it twice.
4. **Proactive refresh** about 60 s before `expiresIn` elapses; reactive refresh on a `401 AUTHENTICATION_REQUIRED`, then the original request is replayed once.
5. **Refresh failure** (`401 INVALID_REFRESH_TOKEN`) → clear state, redirect to `/login?reason=expired&next=<path>`.
6. `/api/auth/*` calls are never retried by the 401 interceptor.

**Local development note:** the refresh cookie is `Secure`. Chrome, Edge and Firefox accept `Secure` cookies on `http://localhost`, so the Vite proxy setup works. Other hosts (e.g. a LAN IP over plain HTTP) will not keep the session across reloads.

## 3. Roles and authorization

Roles (`identity.domain.Role`): `ENGINEER`, `MANAGER`, `PLATFORM_ADMIN`, `AUDITOR`. A user can hold several; the frontend takes them from `GET /api/users/me` (fresh) rather than decoding the JWT.

| Capability | Rule (backend) | UI gate |
|---|---|---|
| Create, edit draft, submit | ENGINEER or MANAGER; edit/submit/cancel only by requester | "New request" visible for ENGINEER/MANAGER; edit/submit/cancel when `requesterId === me.id` and status allows |
| Cancel | requester, status `PENDING_APPROVAL` only | Button only in that state |
| View request, audit trail, decision, provisioning | requester, assigned approver, PLATFORM_ADMIN, AUDITOR | Route open to all authenticated users; 404 handled |
| Approve / reject | assigned approver holding MANAGER, ≠ requester, status `PENDING_APPROVAL` | Decision panel when `assignedApproverId === me.id && hasRole(MANAGER) && status === PENDING_APPROVAL` |
| List `scope=ASSIGNED` | MANAGER | Approvals section |
| List `scope=ALL` | PLATFORM_ADMIN, AUDITOR | Operations section |
| Provisioning retry | PLATFORM_ADMIN, job `FAILED` | Retry button with confirmation |
| Audit search | AUDITOR, PLATFORM_ADMIN | Audit logs |
| `/api/admin/**` | PLATFORM_ADMIN | Administration section |

Platform admins and auditors **cannot create requests** (`403`).

## 4. Endpoint map

Types are written as TypeScript; `UUID` and `Instant` are strings.

### 4.1 Identity

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/auth/login` | `{ email ≤254, password ≤256 }` | `TokenResponse { accessToken, tokenType, expiresIn: number }` |
| POST | `/api/auth/refresh` | – (cookie) | `TokenResponse` |
| POST | `/api/auth/logout` | – (cookie) | `204` |
| POST | `/api/auth/register` | `{ invitationToken ≤128, displayName ≤100, password ≤256 }` | `201 { id, email, displayName, roles: Role[] }` |
| GET | `/api/users/me` | – | `UserResponse` |
| PUT | `/api/users/me` | `{ displayName ≤100 }` | `UserResponse` |
| POST | `/api/users/me/password` | `{ currentPassword, newPassword }` | `204` (all sessions revoked) |
| POST | `/api/auth/password-reset` | `{ token ≤128, newPassword ≤256 }` | `204` (public) |
| GET | `/api/users/directory?ids=a,b,c` | ≤ 100 UUIDs | `DirectoryEntry[]`; unknown IDs are absent (`400 TOO_MANY_IDS` above 100) |

```ts
type UserResponse = { id: UUID; email: string; displayName: string; status: "ACTIVE" | "DISABLED";
  roles: Role[]; managerId: UUID | null; teamId: UUID | null; createdAt: Instant; updatedAt: Instant; version: number };
type DirectoryEntry = { id: UUID; displayName: string; email: string; status: "ACTIVE" | "DISABLED" };
```

Names of requesters, approvers and audit actors come from the directory. Each page collects the distinct IDs it shows and makes one batched lookup; entries are cached per ID (5 min stale time).

### 4.2 Resource catalog

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/resource-types` | – | `ResourceTypeResponse[]` (enabled types only, not paged) |
| GET | `/api/resource-types/{code}` | – | `ResourceTypeResponse` |
| POST | `/api/resource-types/{code}/spec-validation` | `{ environment, spec }` | `200 { valid: true }` or `422 INVALID_RESOURCE_SPEC` / `ENVIRONMENT_NOT_ALLOWED` with `errors` |

```ts
type ResourceTypeCode = "AWS_ACCOUNT" | "GCP_PROJECT" | "K8S_NAMESPACE";
type CloudProvider = "AWS" | "GCP" | "KUBERNETES";
type Environment = "DEVELOPMENT" | "STAGING" | "PRODUCTION";
type ResourceTypeResponse = { code: ResourceTypeCode; provider: CloudProvider; displayName: string;
  description: string; allowedEnvironments: Environment[]; environments: EnvironmentPolicy[] };
// One entry per allowed environment; denied environments are omitted.
type EnvironmentPolicy = { environment: Environment; maxActivePerUser: number | null;
  constraints: Record<string, unknown> | null };
```

### 4.3 Resource specs (per type)

Validated server-side by `resourcecatalog.spec.*Validator`; unknown fields are rejected. Violations come back as `errors[].field = "spec.<name>"`. The frontend Zod schemas mirror the format rules and are **built per environment from `environments[].constraints`**: the AWS OU and GCP folder fields become selects of the allowed values, the AWS email is checked against `allowedEmailDomains`, and K8s CPU and memory get the policy maximums. The server re-checks everything on submit and again on approval.

| Type | Field | Required | Rule |
|---|---|---|---|
| `AWS_ACCOUNT` | `accountName` | yes | 1–50 printable characters |
| | `email` | yes | email, 6–64 chars; domain must be in the policy's `allowedEmailDomains` if set |
| | `organizationalUnitId` | yes | `^ou-[0-9a-z]{4,32}-[a-z0-9]{8,32}$`; must be in policy's `allowedOrganizationalUnitIds` |
| `GCP_PROJECT` | `projectId` | yes | `^[a-z][a-z0-9-]{4,28}[a-z0-9]$`, must not contain `google` |
| | `displayName` | no | `^[A-Za-z0-9' !-]{4,30}$` |
| | `folderId` | yes | `^[0-9]{1,20}$`; must be in policy's `allowedFolderIds` |
| `K8S_NAMESPACE` | `namespace` | yes | DNS label `^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$`, not `kube-*`, not `default` |
| | `cpuCores` | yes | number > 0, ≤ 3 decimals, ≤ policy `maxCpuCores` |
| | `memoryGi` | yes | number > 0, ≤ 3 decimals, ≤ policy `maxMemoryGi` |

Spec JSON ≤ 16 KB. Justification: 10–2000 characters (Bean Validation, `400 VALIDATION_FAILED`, `errors[].field = "justification"`).

### 4.4 Requests and approval

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/requests` | `{ resourceType, environment, spec, justification }` | `201 RequestResponse` (status `DRAFT`) |
| GET | `/api/requests` | `scope=MINE|ASSIGNED|ALL` (default `MINE`), `status` (repeatable), `resourceType`, `environment`, `q` (≤100, case-insensitive substring of request number or justification), `page`, `size`, `sort ∈ {createdAt, submittedAt, requestNumber, status}` (default `createdAt,desc`) | `PageResponse<RequestResponse>` |
| GET | `/api/requests/{id}` | – | `RequestResponse` |
| PUT | `/api/requests/{id}` | `{ environment, spec, justification }` (draft only) | `RequestResponse` |
| DELETE | `/api/requests/{id}` | – (requester, draft only) | `204`; otherwise `422 REQUEST_NOT_DELETABLE` |
| POST | `/api/requests/{id}/submit` | – | `RequestResponse` (`PENDING_APPROVAL`) |
| POST | `/api/requests/{id}/cancel` | – | `RequestResponse` (`CANCELLED`) |
| POST | `/api/requests/{id}/approve` | optional `{ comment ≤2000 }` | `DecisionResponse` |
| POST | `/api/requests/{id}/reject` | `{ comment: required, ≤2000 }` | `DecisionResponse` |
| GET | `/api/requests/{id}/approval` | – | `DecisionResponse`, or `404 DECISION_NOT_FOUND` while undecided (UI: "awaiting decision") |
| GET | `/api/requests/{id}/audit` | `page`, `size` (oldest first, sort ignored) | `PageResponse<AuditEventResponse>` |

```ts
type RequestStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "CANCELLED"
  | "PROVISIONING" | "PROVISIONED" | "PROVISIONING_FAILED";
type RequestResponse = { id: UUID; requestNumber: string /* CF-2026-000123 */; requesterId: UUID;
  assignedApproverId: UUID | null; resourceType: ResourceTypeCode; environment: Environment;
  spec: Record<string, unknown>; justification: string; status: RequestStatus;
  submittedAt: Instant | null; decidedAt: Instant | null; createdAt: Instant; updatedAt: Instant };
type DecisionResponse = { id: UUID; requestId: UUID; approverId: UUID; decision: "APPROVED" | "REJECTED";
  comment: string | null; decidedAt: Instant };
```

State machine (`RequestStatus.canTransitionTo`):
`DRAFT → PENDING_APPROVAL → APPROVED | REJECTED | CANCELLED`; `APPROVED → PROVISIONING → PROVISIONED | PROVISIONING_FAILED`; `PROVISIONING_FAILED → PROVISIONING` (admin retry). Drafts are deleted rather than cancelled; only `PENDING_APPROVAL` can be cancelled.

Submit-time errors the UI must explain (all `422`): `REQUESTER_HAS_NO_MANAGER`, `APPROVER_UNAVAILABLE`, `QUOTA_EXCEEDED`, `INVALID_RESOURCE_SPEC`, `ENVIRONMENT_NOT_ALLOWED`, `RESOURCE_TYPE_DISABLED`. Decision errors: `SELF_APPROVAL_FORBIDDEN` (422), `INVALID_STATE_TRANSITION` (422, e.g. already decided or cancelled), `403 ACCESS_DENIED`.

### 4.5 Provisioning

| Method | Path | Response |
|---|---|---|
| GET | `/api/requests/{id}/provisioning` | `ProvisioningResponse`, or `404 PROVISIONING_NOT_FOUND` before approval (UI: "not started") |
| POST | `/api/requests/{id}/provisioning/retry` | `RetryResponse { status, nextRunAt }` (PLATFORM_ADMIN, job `FAILED`) |
| GET | `/api/provisioning-jobs` | `status` (repeatable), `resourceType`, `page`, `size` (PLATFORM_ADMIN, AUDITOR; fixed order, most recently changed first) → `PageResponse<ProvisioningJobSummary>` |

```ts
type JobStatus = "QUEUED" | "RUNNING" | "AWAITING_PROVIDER" | "SUCCEEDED" | "FAILED";
type ProvisioningResponse = { status: JobStatus; attemptCount: number; nextRunAt: Instant | null;
  lastErrorCode: string | null; lastErrorMessage: string | null; awaitingProvider: boolean;
  result: { externalResourceId: string; details: Record<string, unknown>; createdAt: Instant } | null;
  attempts: { attemptNumber: number; kind: "START" | "POLL";
    outcome: "SUCCEEDED" | "IN_PROGRESS" | "RETRYABLE_FAILURE" | "PERMANENT_FAILURE";
    startedAt: Instant; finishedAt: Instant; errorCode: string | null; errorMessage: string | null }[] };
```

```ts
type ProvisioningJobSummary = { jobId: UUID; requestId: UUID; requestNumber: string; resourceType: ResourceTypeCode;
  status: JobStatus; attemptCount: number; nextRunAt: Instant | null; lastErrorCode: string | null;
  lastErrorMessage: string | null; createdAt: Instant; updatedAt: Instant };
```

The jobs list shows `createdAt` as "queued" and `updatedAt` as "last change". There are no `startedAt`/`completedAt` fields on the job: the UI derives "started" from the first attempt's `startedAt` and "completed" from the last attempt's `finishedAt` when the job is `SUCCEEDED` or `FAILED`. Error messages are already sanitized by the backend adapters. While a request is `APPROVED` or `PROVISIONING`, the detail page polls this endpoint (every 5 s, stopping on a final state).

### 4.6 Notifications

| Method | Path | Response |
|---|---|---|
| GET | `/api/notifications?unreadOnly=` | `PageResponse<NotificationResponse>` (newest first) |
| GET | `/api/notifications/unread-count` | `{ unread: number }` |
| POST | `/api/notifications/{id}/read` | `NotificationResponse` |
| POST | `/api/notifications/read-all` | `{ marked: number }` |

```ts
type NotificationResponse = { id: UUID; type: "APPROVAL_REQUESTED" | "REQUEST_APPROVED" | "REQUEST_REJECTED"
  | "PROVISIONING_SUCCEEDED" | "PROVISIONING_FAILED"; title: string; body: string; requestId: UUID | null;
  readAt: Instant | null; createdAt: Instant };
```

No push channel exists: the unread count is polled (60 s, and on window focus).

### 4.7 Audit

`GET /api/audit-events` (AUDITOR, PLATFORM_ADMIN) with `entityType`, `entityId`, `actorId`, `action`, `from`, `to` (ISO date-time), `page`, `size`. Fixed newest-first order.

```ts
type AuditEventResponse = { id: UUID; occurredAt: Instant; actorType: "USER" | "SYSTEM"; actorId: UUID | null;
  action: AuditAction; entityType: "RESOURCE_REQUEST" | "USER" | "INVITATION" | "DEPARTMENT" | "TEAM" | "RESOURCE_TYPE";
  entityId: UUID; correlationId: string | null; details: Record<string, unknown> };
```

`AuditAction` (see `audit.domain.AuditAction`) now also includes `REQUEST_DELETED`, `PROFILE_UPDATED`, `PASSWORD_CHANGED`, `PASSWORD_RESET_ISSUED` and `PASSWORD_RESET_COMPLETED`. Values the frontend doesn't know yet are shown humanized rather than rejected. There are **no before/after snapshots**; `details` holds action-specific fields (e.g. `approverId`, `comment`, `requestNumber`) and is rendered as a key/value list.

### 4.8 Administration (`PLATFORM_ADMIN`)

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/admin/users` | `status`, `teamId`, `managerId`, paging, `sort ∈ {email, displayName, status, createdAt}` | `PageResponse<UserResponse>` |
| GET | `/api/admin/users/{id}` | – | `UserResponse` + ETag |
| PUT | `/api/admin/users/{id}/manager` | `{ managerId: UUID | null }` | `UserResponse` |
| PUT | `/api/admin/users/{id}/team` | `{ teamId: UUID | null }` | `UserResponse` |
| PUT | `/api/admin/users/{id}/roles` | `{ roles: Role[] (non-empty) }` | `UserResponse` |
| POST | `/api/admin/users/{id}/disable` · `/enable` | – | `UserResponse` |
| POST | `/api/admin/users/{id}/password-reset` | – | `201 { token, expiresAt }` (`no-store`, shown once; `422 USER_DISABLED`) |
| POST | `/api/admin/invitations` | `{ email, roles (non-empty), managerId?, teamId? }` | `201 { invitation: InvitationResponse, token }` (token shown once) |
| GET | `/api/admin/invitations` | `status ∈ {PENDING, ACCEPTED, REVOKED}`, paging, `sort ∈ {email, status, expiresAt, createdAt}` | `PageResponse<InvitationResponse>` |
| GET | `/api/admin/invitations/{id}` · POST `/{id}/revoke` | – | `InvitationResponse` |
| POST · GET | `/api/admin/departments` · `/{id}` | `{ name ≤100 }`; paging, `sort ∈ {name, createdAt}` | `DepartmentResponse` / page |
| PUT | `/api/admin/departments/{id}/name` | `{ name }` | `DepartmentResponse` |
| POST · GET | `/api/admin/teams` · `/{id}` | `{ departmentId, name }`; `?departmentId=` | `TeamResponse` / page |
| PUT | `/api/admin/teams/{id}/name` | `{ name }` | `TeamResponse` |
| GET | `/api/admin/resource-types` · `/{code}` | – | `AdminResourceTypeResponse[]` / one + ETag |
| PUT | `/api/admin/resource-types/{code}` | `{ displayName ≤100, description ≤1000, enabled }` | `AdminResourceTypeResponse` |
| PUT | `/api/admin/resource-types/{code}/policies/{environment}` | `{ allowed, maxActivePerUser?: >0, constraints }`; If-Match = policy `version` | `PolicyResponse` |

```ts
type InvitationResponse = { id: UUID; email: string; status: "PENDING" | "ACCEPTED" | "REVOKED"; expired: boolean;
  roles: Role[]; managerId: UUID | null; teamId: UUID | null; invitedBy: UUID; expiresAt: Instant; createdAt: Instant };
type DepartmentResponse = { id: UUID; name: string; createdAt: Instant; updatedAt: Instant; version: number };
type TeamResponse = { id: UUID; departmentId: UUID; name: string; createdAt: Instant; updatedAt: Instant; version: number };
type AdminResourceTypeResponse = { code: ResourceTypeCode; provider: CloudProvider; displayName: string;
  description: string; enabled: boolean; version: number; policies: PolicyResponse[] };
type PolicyResponse = { environment: Environment; allowed: boolean; maxActivePerUser: number | null;
  constraints: Record<string, unknown> | null; version: number | null /* null = not stored yet */ };
```

Policy constraints per type: AWS `{ allowedOrganizationalUnitIds: string[], allowedEmailDomains?: string[] }` (OU list required when allowed); GCP `{ allowedFolderIds: string[] }` (required when allowed); K8s `{ maxCpuCores: number, maxMemoryGi: number }` (both required when allowed). Errors: `422 INVALID_POLICY_CONSTRAINTS` with `errors[].field = "constraints.<name>"`.

Admin business errors: `SELF_LOCKOUT`, `MANAGER_HAS_REPORTS`, `MANAGER_CYCLE`, `MANAGER_SELF_ASSIGNMENT`, `MANAGER_NOT_ELIGIBLE`, `DEPARTMENT_NAME_TAKEN`, `TEAM_NAME_TAKEN`, `*_NOT_FOUND`.

## 5. Error model

RFC 9457 `application/problem+json`:

```ts
type ProblemDetail = { type?: string; title?: string; status: number; detail?: string; instance?: string;
  code: string; correlationId: string | null; timestamp: Instant;
  errors?: { field: string; message: string }[] };
```

| Status | Frontend handling |
|---|---|
| 400 `VALIDATION_FAILED` / 422 with `errors` | Map `errors[].field` onto form fields (`spec.namespace` → `spec.namespace` in RHF); remaining errors in a form-level alert |
| 401 `AUTHENTICATION_REQUIRED` | Silent refresh + single replay; on failure → login with "session expired" |
| 401 `INVALID_CREDENTIALS` | Login form error (generic, no account hints) |
| 403 `ACCESS_DENIED` | Toast / inline "You don't have permission"; on a route load → Unauthorized page |
| 404 | Entity "not found or not accessible" state |
| 409 | "Changed concurrently. Reload and try again." with reload action |
| 412 | Same as 409, refetch entity to get the fresh ETag |
| 422 business code | `detail` shown verbatim (backend guarantees it is safe and user-facing), code-specific next action where useful (e.g. `REQUESTER_HAS_NO_MANAGER` → "contact an administrator") |
| 429 | "Too many attempts, try again in N s" using `Retry-After` |
| 5xx / network | Generic message + correlation ID + retry button |

`detail` is safe to display (backend never puts internals in it); a 500's detail is generic by design.

## 6. Pagination and filtering summary

| List | Filters | Sort | Default |
|---|---|---|---|
| Requests | `scope`, `status` (repeatable), `resourceType`, `environment`, `q` | createdAt, submittedAt, requestNumber, status | createdAt desc |
| Provisioning jobs | `status` (repeatable), `resourceType` | fixed: updatedAt desc | |
| Request audit trail | – | fixed oldest first | |
| Audit events | entityType, entityId, actorId, action, from, to | fixed newest first | |
| Notifications | unreadOnly | fixed newest first | |
| Users | status, teamId, managerId | email, displayName, status, createdAt | email asc |
| Invitations | status | email, status, expiresAt, createdAt | createdAt desc |
| Departments | – | name, createdAt | name asc |
| Teams | departmentId | name, createdAt | name asc |

URL search params hold the list state (`?status=…&page=2&sort=submittedAt,desc`), so lists are linkable and survive reloads.

"Filter by provider" maps to `resourceType` because each type belongs to exactly one provider.

## 7. Derived views (no dedicated endpoint, built from real data)

| View | How |
|---|---|
| Dashboard counters | `GET /api/requests?scope=…&status=…&size=1` → `totalElements`, one query per counter, cached 30 s. Repeatable `status` makes "approved and beyond" a single query |
| Approval history | `scope=ASSIGNED&status=APPROVED&status=REJECTED&status=PROVISIONING&status=PROVISIONED&status=PROVISIONING_FAILED` |
| Resource inventory | `status=PROVISIONED` (`MINE` for requesters, `ALL` for admins/auditors); result details on the request detail page |
| Display names | `GET /api/users/directory`, one batched call per page |

## 8. Backend gaps

Identified in Phase 1; status re-verified against the code after the backend changes.

| # | Gap | Status |
|---|---|---|
| G1 | Resolve user IDs to names for non-admins | **Closed**: `GET /api/users/directory` |
| G2 | Requester team/department visible to managers | **Open**: `DirectoryEntry` has no team or department, so the approval page shows name and email only. Smallest change: add `teamName`/`departmentName` to `DirectoryEntry` |
| G3 | Policy constraints visible to requesters | **Closed**: `ResourceTypeResponse.environments` |
| G4 | Text search on requests | **Closed**: `q` |
| G5 | Multi-value status filter | **Closed**: repeatable `status` |
| G6 | Provisioning job listing | **Closed**: `GET /api/provisioning-jobs` |
| G7 | Forgot/change password, profile edit | **Closed**: admin-issued reset token, `POST /api/users/me/password`, `PUT /api/users/me` |
| G8 | Delete drafts | **Closed**: `DELETE /api/requests/{id}` |
| G9 | Aggregate stats endpoint | **Open, not needed**: counters use `size=1` queries (section 7) |
| G10 | CORS | **Not needed**: same origin through the proxy |
