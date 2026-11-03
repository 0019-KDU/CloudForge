# Frontend architecture

> Status: implemented (all phases). The API contract is
> in [api-integration.md](api-integration.md); the roadmap is in [implementation-plan.md](implementation-plan.md).

## Overview

A single-page React app served from the same origin as the API. The Spring Boot backend is the only
source of truth and the only security boundary: the frontend never calls cloud providers, holds no
secrets, and treats its own role checks as presentation.

```
Browser ── /api/** ──► (Vite proxy | reverse proxy) ──► Spring Boot API ──► PostgreSQL / providers
        └─ everything else ──► SPA (index.html + hashed assets)
```

## Layers

| Layer | Location | Responsibility |
|---|---|---|
| App | `src/app` | Providers, router, guards, layouts (shell, sidebar, breadcrumbs) |
| Features | `src/features/<area>` | API functions, query hooks, pages and feature components for one business area |
| Shared UI | `src/components` | `ui/` (shadcn primitives, vendored), `shared/` (page header, form field…), `feedback/` (empty, error, loading, confirm) |
| Infrastructure | `src/lib` | HTTP client, error normalization, query client, formatting, permissions, validation helpers |
| Contract | `src/types/api.ts` | DTOs and enums exactly as the backend sends them |

Dependency direction: `app → features → components/lib → types`. Features don't import each other's
internals except for small public hooks (e.g. `features/users/directory`).

## Server state

TanStack Query owns all server data. Defaults (`lib/api/query-client.ts`): 30 s stale time, refetch on
focus, no retries for 4xx, **no automatic mutation retries** (approvals, submissions and provisioning
retries are business operations). Sensitive mutations are never optimistic: the UI waits for the
server, then invalidates the affected queries.

Local UI state stays in components. Cross-cutting client state is limited to two contexts: auth
(`features/auth`) and theme (`app/providers`). There is no global store.

## HTTP client

`lib/api/client.ts` is the only Axios instance:

- `baseURL` from `VITE_API_BASE_URL` (default `/api`), `withCredentials` for the refresh cookie.
- Adds `Authorization: Bearer …` and a fresh `X-Correlation-Id` (UUID) to every call.
- Serializes arrays as repeated params (`?status=A&status=B`), as Spring expects.
- On `401`, refreshes the token once and replays the request; if the refresh fails, it ends the session.
- Rejects with `ApiError` (`lib/api/problem.ts`), which normalizes RFC 9457 problem bodies into
  `kind`, `code`, a safe `message`, `fieldErrors`, `correlationId` and `retryAfter`. 5xx details are
  never shown.

The client knows nothing about tokens: `features/auth/session.ts` plugs in through `configureAuth`, so
the dependency points one way.

## Authentication

Bearer JWT access tokens (10 min) plus a rotating `HttpOnly` refresh cookie (ADR-0003).

| Concern | Implementation |
|---|---|
| Token storage | Module memory in `session.ts`; never web storage |
| Page load | `resume.ts` calls `/auth/refresh` once (module-level promise, so StrictMode can't send it twice), then `/users/me` |
| Expiry | Proactive refresh 60 s before expiry; reactive refresh on 401 |
| Rotation safety | One in-flight refresh per tab (shared promise) and across tabs (`navigator.locks`), because a replayed refresh token revokes the whole session |
| Session loss | `session.ts` emits `expired`, `AuthProvider` clears state and caches, the guard redirects to `/login?reason=expired&next=…` |
| Logout | `POST /auth/logout`, then local cleanup even if the call fails |
| Password change | Backend revokes all sessions; the app ends the local session and asks for a fresh sign-in |
| One-time tokens | Invitation and reset links carry the token in the URL fragment (`#token=`), which is removed from the address bar on read |

`AuthProvider` exposes `status` (`loading | authenticated | anonymous`), `user` (from `/users/me`, not
the JWT), `login`, `logout` and `endSession`.

## Routing

React Router data router (`app/router/routes.tsx`); every page is a lazy route module. Guards:

- `RequireAuth` — loader while resuming; anonymous users go to `/login?next=…`.
- `RequireRole` — renders the Unauthorized page in place when the user lacks every listed role.
- `PublicOnly` — signed-in users skip the auth pages; `?next=` only accepts same-app paths.

Breadcrumbs come from `handle.crumb` on route objects. See [routing-and-navigation.md](routing-and-navigation.md)


## Error handling

- Query failures: `ErrorState` (friendly text, retry for transient errors, correlation ID).
- Form failures: `applyServerErrors` maps `errors[].field` and field-specific business codes onto
  inputs; anything else goes to a form-level alert. Input is preserved.
- Render errors and failed lazy chunks: `RouteErrorBoundary` per route tree, never showing raw errors.

## Testing

Vitest + Testing Library + MSW. Tests render the real route tree (`test/render.tsx`) against MSW
handlers that return backend-shaped fixtures, and select by role and label. See [testing.md](testing.md).
