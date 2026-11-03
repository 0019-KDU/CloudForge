# Routing and navigation

Routes live in `frontend/src/app/router/routes.tsx`; the sidebar in `navigation.ts`. Both take their
role lists from one table, `access.ts`, so a page and its menu entry can't disagree. Role checks in the
frontend only decide what to show. The backend authorizes every call again, hides requests the caller
may not see (404) and refuses forbidden operations (403).

## Access groups

| Group | Roles | Backend rule it mirrors |
|---|---|---|
| `requester` | ENGINEER, MANAGER | `RequestAccessPolicy.requireCanCreate` |
| `manager` | MANAGER | `scope=ASSIGNED` |
| `oversight` | PLATFORM_ADMIN, AUDITOR | `scope=ALL`, `/api/audit-events`, `/api/provisioning-jobs` |
| `admin` | PLATFORM_ADMIN | `/api/admin/**` |

Users with several roles see the union.

## Route map

| Path | Page | Access | Main API calls |
|---|---|---|---|
| `/login` · `/register#token=` · `/reset-password#token=` | Auth pages | public (signed-in users are sent on) | `/auth/*` |
| `/` | Overview (sections per role) | any | request counts, `/provisioning-jobs` count |
| `/requests` | My requests | requester | `GET /requests?scope=MINE` |
| `/requests/new` (`?type=`) | New request wizard | requester | `/resource-types`, `spec-validation`, `POST /requests`, `/submit` |
| `/requests/:id` | Request detail, decision, provisioning, history | any (404 if hidden) | request, `/approval`, `/provisioning`, `/audit` |
| `/requests/:id/edit` | Edit draft | requester | `PUT /requests/:id`, `/submit` |
| `/catalog` | Resource catalog | any | `/resource-types` |
| `/inventory` | Provisioned resources (own, or all for oversight) | any | `GET /requests?status=PROVISIONED` |
| `/approvals` | Pending approvals | manager | `scope=ASSIGNED&status=PENDING_APPROVAL` |
| `/approvals/history` | Decided requests | manager | `scope=ASSIGNED&status=…` |
| `/operations/requests` | All requests | oversight | `scope=ALL` |
| `/operations/provisioning` (`?view=failed\|active\|succeeded`) | Provisioning jobs, retry | oversight (retry: admin) | `/provisioning-jobs`, `/provisioning/retry` |
| `/audit` | Audit log | oversight | `/audit-events` |
| `/admin/users` · `/admin/users/:id` | Users, roles, manager, team, enable/disable, password reset | admin | `/admin/users/**` |
| `/admin/invitations` | Invitations | admin | `/admin/invitations/**` |
| `/admin/organization` | Departments and teams | admin | `/admin/departments`, `/admin/teams` |
| `/admin/catalog` | Resource types and policies | admin | `/admin/resource-types/**` |
| `/profile` | Profile, password, appearance | any | `/users/me`, `/users/me/password` |
| `/unauthorized`, `*` | Access denied, not found | any | – |

There is one URL per request. The approval review happens on `/requests/:id`: the decision panel
appears only for the assigned approver (holding MANAGER, not the requester) while the request is
pending. Notifications link there too.

## Guards

- `RequireAuth`: a loader while the session resumes; anonymous users go to `/login?next=<path>` (plus `reason=expired|password-changed`).
- `RequireRole`: renders the Unauthorized page in place, keeping the URL.
- `PublicOnly`: signed-in users skip the auth pages. `next` only accepts same-app paths (`/x`, never `//host` or `/\host`).

## Navigation

| Section | Items |
|---|---|
| – | Overview |
| Resource management | My requests\*, New request\*, Resource catalog |
| Approvals | Pending approvals†, Approval history† |
| Operations | All requests‡, Provisioning jobs‡, Resource inventory |
| Governance | Audit logs‡, Users§, Invitations§, Departments & teams§, Resource policies§ |
| System | Profile |

\* requester · † manager · ‡ oversight · § admin. Empty sections are hidden.

## List state in the URL

Lists keep `page` (1-based in the URL), `size`, `sort=field,dir` and their filters in the query
string (`hooks/useListParams.ts`), so a filtered view can be bookmarked or shared and survives a
reload. Changing a filter or the sort resets to page 1. Sorting is offered only on columns the backend
whitelists.

## Breadcrumbs and titles

Routes declare `handle: { crumb }`; the top bar renders the matched chain. Every page sets
`document.title` through `PageHeader` ("Pending approvals · CloudForge").

## Code splitting

Each page is a lazy route module. Vendor code is split into long-lived chunks (`react`, `data`, `ui`);
Recharts loads only with the platform overview chart.
