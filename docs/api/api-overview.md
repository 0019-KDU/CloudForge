# API overview

> Status: complete backend API. OpenAPI: `/v3/api-docs` and `/swagger-ui.html` (springdoc 3.1.1), enabled in the `local` profile only (`springdoc.api-docs.enabled`).

## Conventions

- JSON over HTTPS; base path `/api`. Authenticate with `Authorization: Bearer <accessToken>` ([ADR-0003](../decisions/0003-jwt-authentication.md)). No CSRF token is needed.
- Endpoints model business operations (`POST /requests/{id}/approve`), not field updates. No endpoint sets `status`, `requesterId` or `assignedApproverId`; such body fields are ignored.
- Lists are paginated: `?page=0&size=20&sort=createdAt,desc`. `size` is capped at 100. Sort fields are whitelisted per endpoint (otherwise `400 UNSUPPORTED_SORT`). Audit and notification lists use a fixed newest-first order.
- Every response carries `X-Correlation-Id`. Clients may send one (8–64 chars `[A-Za-z0-9._-]`).
- Resources a caller may not see answer `404`, not `403`.
- **Optimistic concurrency for admin edits:** admin resources (users, departments, teams, resource types, policies) return `ETag: "<version>"` and a `version` field. Send it back as `If-Match` on updates; if someone else changed the resource meanwhile, the update is refused with `412 PRECONDITION_FAILED` instead of silently overwriting. `If-Match` is optional; without it the last write wins.
- **Rate limit:** `POST /api/auth/login` and `/api/auth/register` allow 20 attempts per client address per 5 minutes (all instances combined), then answer `429 TOO_MANY_ATTEMPTS` with `Retry-After`.

## Errors

All errors are `application/problem+json` (RFC 9457) with `code`, `correlationId` and `timestamp`, plus `errors: [{field, message}]` for field-level problems.

| Status | When | Example `code` |
|---|---|---|
| 400 | Malformed body, Bean Validation, unknown enum or sort | `VALIDATION_FAILED`, `BAD_REQUEST`, `UNSUPPORTED_SORT` |
| 401 | Missing or invalid token, bad credentials, invalid refresh token | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 403 | Authenticated but not allowed (role, or visible but not yours to change) | `ACCESS_DENIED` |
| 404 | Not found, or not visible to the caller | `REQUEST_NOT_FOUND`, `PROVISIONING_NOT_FOUND` |
| 412 | `If-Match` version is stale | `PRECONDITION_FAILED` |
| 429 | Too many authentication attempts from one client | `TOO_MANY_ATTEMPTS` (with `Retry-After`) |
| 409 | Duplicate or concurrent modification | `CONCURRENT_MODIFICATION`, `DATA_CONFLICT`, `DEPARTMENT_NAME_TAKEN` |
| 422 | Business rule violated, invalid spec or policy | `INVALID_STATE_TRANSITION`, `SELF_APPROVAL_FORBIDDEN`, `REQUESTER_HAS_NO_MANAGER`, `QUOTA_EXCEEDED`, `INVALID_RESOURCE_SPEC`, `ENVIRONMENT_NOT_ALLOWED`, `SELF_LOCKOUT` |
| 500 | Unexpected; details only in server logs | `INTERNAL_ERROR` |

## Endpoints

### Authentication and profile
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/login` | public | `{email, password}` → `{accessToken, tokenType, expiresIn}` + `cf_refresh` cookie |
| POST | `/api/auth/refresh` | cookie | rotates the refresh token; reuse revokes the family |
| POST | `/api/auth/logout` | cookie | `204`; idempotent |
| POST | `/api/auth/register` | invitation token | `{invitationToken, displayName, password}` → `201` |
| POST | `/api/auth/password-reset` | reset token | `{token, newPassword}` → `204`; token issued by an admin; rate limited |
| GET · PUT | `/api/users/me` | any | current profile · `{displayName}` |
| POST | `/api/users/me/password` | any | `{currentPassword, newPassword}` → `204`; signs out all sessions; rate limited per user |
| GET | `/api/users/directory?ids=a,b,c` | any | up to 100 IDs → `[{id, displayName, email, status, teamId, teamName, departmentId, departmentName}]`; used to show names instead of IDs (requesters, approvers, audit actors); 3 queries regardless of size |

### Resource catalog
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/resource-types` · `/{code}` | any | enabled types, `allowedEnvironments`, and `environments: [{environment, maxActivePerUser, constraints}]` for allowed environments (approved OUs and folders, CPU and memory caps) so forms can offer valid choices |
| POST | `/api/resource-types/{code}/spec-validation` | any | `{environment, spec}` → `200` or `422` with field errors |

### Requests and approval
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/requests` | ENGINEER, MANAGER | `{resourceType, environment, spec, justification}` → `201` DRAFT |
| GET | `/api/requests` | any | `?scope=MINE` (default) · `ASSIGNED` (MANAGER) · `ALL` (ADMIN, AUDITOR); `q` = case-insensitive text in request number or justification; `status` repeatable (`?status=APPROVED&status=PROVISIONING`); `resourceType`, `environment`; sort `createdAt`, `submittedAt`, `requestNumber`, `status` |
| GET | `/api/requests/summary` | any | `?scope=` as for the list → `{scope, total, byStatus: {DRAFT: n, …}}` (every status present); one GROUP BY query |
| GET | `/api/requests/{id}` | viewer | |
| PUT · DELETE | `/api/requests/{id}` | requester | edit draft · delete draft (`204`; submitted requests can only be cancelled) |
| POST | `/api/requests/{id}/submit` · `/cancel` | requester | |
| POST | `/api/requests/{id}/approve` | assigned manager | optional `{comment}`; queues provisioning |
| POST | `/api/requests/{id}/reject` | assigned manager | `{comment}` required |
| GET | `/api/requests/{id}/approval` | viewer | the decision |
| GET | `/api/requests/{id}/audit` | viewer | audit trail, oldest first |
| GET | `/api/requests/{id}/provisioning` | viewer | job status, attempts, result (operation IDs stay internal) |
| POST | `/api/requests/{id}/provisioning/retry` | PLATFORM_ADMIN | after a final failure |

### Provisioning operations
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/provisioning-jobs/summary` | PLATFORM_ADMIN, AUDITOR | `{total, byStatus}` |
| GET | `/api/provisioning-jobs` | PLATFORM_ADMIN, AUDITOR | `status` repeatable, `resourceType`; newest change first; includes `requestNumber` and last error |

### Notifications
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/notifications` | any | own only; `?unreadOnly=true` |
| GET | `/api/notifications/unread-count` | any | |
| POST | `/api/notifications/{id}/read` · `/read-all` | any | own only (others' IDs → 404) |

### Audit
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/audit-events` | AUDITOR, PLATFORM_ADMIN | filters `entityType`, `entityId`, `actorId`, `action`, `from`, `to` (ISO-8601) |

### Administration (`PLATFORM_ADMIN`; URL rule plus `@PreAuthorize`)
| Method | Path | Notes |
|---|---|---|
| POST · GET | `/api/admin/departments` · `/{id}` · PUT `/{id}/name` | |
| POST · GET | `/api/admin/teams` · `/{id}` · PUT `/{id}/name` | `?departmentId=` |
| GET | `/api/admin/users` · `/{id}` | filters `status`, `teamId`, `managerId` |
| PUT | `/api/admin/users/{id}/manager` · `/team` · `/roles` | hierarchy rules; role change revokes refresh tokens |
| POST | `/api/admin/users/{id}/disable` · `/enable` | `SELF_LOCKOUT`, `MANAGER_HAS_REPORTS` |
| POST · GET | `/api/admin/invitations` · `/{id}` · POST `/{id}/revoke` | token shown once, `no-store` |
| POST | `/api/admin/users/{id}/password-reset` | one-time reset token (24 h), shown once, `no-store`; invalidates older tokens |
| GET · PUT | `/api/admin/resource-types` · `/{code}` · PUT `/{code}/policies/{environment}` | policy constraints validated per type |

### Operations
`GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` and `/actuator/info` are public; `/actuator/metrics` requires authentication.
