# Frontend implementation plan

> Status: all phases implemented. API contract: [api-integration.md](api-integration.md).

## 1. Stack

| Concern | Choice | Notes |
|---|---|---|
| Build | Vite + React + TypeScript (`strict`, `noUncheckedIndexedAccess`) | Latest stable versions at install time, pinned by the lockfile |
| Routing | React Router (data router, `createBrowserRouter`) | Lazy route modules per feature |
| Server state | TanStack Query | Query-key factories per feature; no Redux |
| HTTP | Axios | One instance: auth header, correlation ID, refresh-on-401, problem-detail normalization |
| UI | Tailwind CSS + shadcn/ui (Radix primitives) + Lucide | Components copied into `components/ui` and themed by tokens |
| Forms | React Hook Form + Zod | One schema per resource type; server `errors[]` mapped onto fields |
| Charts | Recharts | Only where a chart adds information (request status breakdown); no decorative charts |
| Tests | Vitest + React Testing Library + user-event + MSW | MSW handlers return backend-shaped fixtures typed from `types/api` |
| Fonts | Self-hosted via `@fontsource-variable` (Inter, JetBrains Mono) | No runtime CDN |
| Package manager | npm | |

## 2. Folder structure

```
frontend/
├── index.html
├── vite.config.ts            # /api proxy → :8080
├── .env.example              # VITE_API_BASE_URL=/api (public, no secrets)
└── src/
    ├── main.tsx
    ├── app/
    │   ├── providers/        # QueryClientProvider, AuthProvider, ThemeProvider, Toaster
    │   ├── router/           # routes.tsx, guards (RequireAuth, RequireRole), route meta (title, breadcrumb)
    │   └── layouts/          # AppShell (sidebar + topbar), AuthLayout
    ├── components/
    │   ├── ui/               # shadcn primitives (button, dialog, table, badge, …)
    │   ├── shared/           # PageHeader, StatusBadge, EnvironmentBadge, ProviderIcon, UserLabel, Timestamp, KeyValueList, CopyButton
    │   ├── tables/           # DataTable (server pagination/sort), TablePagination, FilterBar
    │   └── feedback/         # EmptyState, ErrorState, Skeletons, ConfirmDialog, ErrorBoundary
    ├── features/
    │   ├── auth/             # api, useAuth, LoginPage, RegisterPage, token store, refresh coordinator
    │   ├── dashboard/        # role-aware OverviewPage + widgets
    │   ├── requests/         # api, hooks, MyRequestsPage, NewRequestPage (wizard), RequestDetailPage, spec forms per type
    │   ├── approvals/        # PendingApprovalsPage, ApprovalHistoryPage, DecisionPanel
    │   ├── provisioning/     # ProvisioningPanel (detail), ProvisioningJobsPage, RetryDialog
    │   ├── resources/        # CatalogPage, InventoryPage, admin catalog & policy editors
    │   ├── audit/            # AuditLogPage, AuditTimeline (request detail)
    │   ├── users/            # UsersPage, UserDetailPage, InvitationsPage, OrganizationPage
    │   ├── notifications/    # NotificationsMenu, unread polling
    │   └── settings/         # ProfilePage (profile + appearance)
    ├── hooks/                # useDebounce, useUrlState, useDocumentTitle
    ├── lib/
    │   ├── api/              # client.ts, problem.ts (ApiError), query-client.ts, pagination helpers
    │   ├── utils/            # cn, format (dates, ids), permissions (hasRole, canDecide, canCancel…)
    │   └── validations/      # shared zod pieces (justification, email)
    ├── types/                # api.ts: DTOs and enums exactly as in api-integration.md
    ├── styles/               # globals.css: tokens (light/dark), base layer
    └── test/                 # setup, MSW server + handlers, fixtures, render helpers
```

Each feature owns `api.ts` (plain functions calling the client), `queries.ts` (query keys + hooks), pages and components. Pages compose hooks and components; they don't call Axios directly.

## 3. Routes and role-based navigation

> The implemented, authoritative route map is [routing-and-navigation.md](routing-and-navigation.md); this table is the original plan.

Navigation is the union of the user's roles. Route guards use the same permission helpers as the nav, and the backend still decides every call.

| Section | Item | Route | ENG | MGR | ADMIN | AUD | Data |
|---|---|---|:-:|:-:|:-:|:-:|---|
| – | Overview | `/` | ✓ | ✓ | ✓ | ✓ | role-specific widgets |
| Resource management | My requests | `/requests` | ✓ | ✓ | | | `scope=MINE` |
| | New request | `/requests/new` | ✓ | ✓ | | | create + submit |
| | Resource catalog | `/catalog` | ✓ | ✓ | ✓ | ✓ | `/resource-types` |
| | Request detail | `/requests/:id` | ✓ | ✓ | ✓ | ✓ | role-aware actions |
| Approvals | Pending approvals | `/approvals` | | ✓ | | | `scope=ASSIGNED&status=PENDING_APPROVAL` |
| | Approval history | `/approvals/history` | | ✓ | | | `scope=ASSIGNED`, decided statuses |
| Operations | All requests | `/operations/requests` | | | ✓ | ✓ | `scope=ALL` |
| | Provisioning jobs | `/operations/provisioning` | | | ✓ | ✓¹ | `/provisioning-jobs` |
| | Resource inventory | `/inventory` | ✓ | ✓ | ✓ | ✓ | `status=PROVISIONED` (MINE or ALL) |
| Governance | Audit logs | `/audit` | | | ✓ | ✓ | `/audit-events` |
| | Users | `/admin/users` · `/:id` | | | ✓ | | admin users |
| | Invitations | `/admin/invitations` | | | ✓ | | admin invitations |
| | Departments & teams | `/admin/organization` | | | ✓ | | admin org |
| | Resource policies | `/admin/catalog` · `/:code` | | | ✓ | | admin resource types + policies |
| System | Profile | `/profile` | ✓ | ✓ | ✓ | ✓ | `/users/me` (edit name, change password) + appearance |
| Public | Login · Register · Reset password | `/login` · `/register#token=…` · `/reset-password#token=…` | | | | | auth |
| – | Unauthorized · Not found | `/unauthorized` · `*` | | | | | |

¹ Auditors see jobs read-only (no retry). Approval review happens on `/requests/:id`: the decision panel appears only for the assigned approver while the request is pending, so there is one canonical URL per request (notifications link there).

No separate Settings page: there are no backend settings for users, so appearance lives under Profile. Registration and password-reset links put the one-time token in the URL fragment (`#token=`), so it never reaches server logs or `Referer` headers; admins copy that link from the invitation or reset dialog.

## 4. Design system

Principles: calm, dense-but-readable, one accent, colour carries meaning only.

- **Tokens** (CSS variables, light and dark; Tailwind maps to them): `background`, `surface`, `surface-muted`, `border`, `foreground`, `muted-foreground`, `primary` (indigo 600 / 400 in dark), `ring`, and semantic `success`, `warning`, `danger`, `info`, each with a `-subtle` background and `-foreground` text.
- **Type**: Inter variable; 13px table/body-small, 14px body, 16/20/24px headings, `tabular-nums` for numbers; JetBrains Mono for request numbers, IDs and spec values.
- **Spacing**: 4px scale; page gutter 24px (16px mobile); card padding 16–20px; table rows 44px.
- **Radius**: 6px controls, 8px cards and dialogs, full for badges. **Elevation**: 1px borders; shadow only on overlays.
- **Status vocabulary** (badge = icon + label, never colour alone):

| Status | Tone | Icon |
|---|---|---|
| DRAFT | neutral | `FilePen` |
| PENDING_APPROVAL | warning | `Clock` |
| APPROVED | info | `CheckCircle` |
| PROVISIONING | info | `Loader` (animated, reduced-motion aware) |
| PROVISIONED | success | `CircleCheckBig` |
| REJECTED | danger | `XCircle` |
| PROVISIONING_FAILED | danger | `AlertTriangle` |
| CANCELLED | neutral (muted) | `Ban` |

  Job statuses use the same tones (QUEUED neutral, RUNNING/AWAITING_PROVIDER info, SUCCEEDED success, FAILED danger). Environments: DEVELOPMENT neutral, STAGING neutral outline, PRODUCTION warning outline (production should stand out in review).
- **Core components**: AppShell, PageHeader (title, description, actions), DataTable (server-driven), FilterBar, StatusBadge, KeyValueList, Timeline, StepIndicator, EmptyState, ErrorState, ConfirmDialog, Skeletons, Toast.
- **Accessibility**: Radix primitives for focus management; visible focus rings; skip link; landmarks (`nav`, `main`); tables with captions and `aria-sort`; forms with labelled fields and `aria-describedby` errors; contrast ≥ 4.5:1 checked for both themes.
- **Responsive**: sidebar collapses to icons ≥ md, becomes a sheet < md; tables scroll horizontally inside their card, with less important columns hidden at small widths.

Documented in `docs/frontend/design-system.md` during Phase 2.

## 5. Key flows

**New request** (`/requests/new`): 1 Resource type (cards from catalog) → 2 Configuration (type-specific form, environment limited to `allowedEnvironments`, debounced server spec-validation) → 3 Justification → 4 Review → **Submit** (`POST /requests`, then `POST /{id}/submit`) or **Save draft** (create only). If submit fails after the draft was created, the user lands on the draft's detail page with the error explained and can edit and resubmit, so no input is lost. Drafts are editable at `/requests/:id/edit`.

**Approval**: Pending approvals table → request detail with decision panel → Approve (optional comment) / Reject (reason required) → confirmation dialog → mutation (button disabled while pending; no optimistic update) → invalidate request, approval, lists, counters → toast.

**Provisioning**: detail page shows a stepper (Submitted → Approved → Provisioning → Provisioned/Failed), job status, attempts table and result (`externalResourceId`, `details`). Polls while non-final. Admin retry behind a confirmation dialog.

## 6. Phases

Every phase ends with `tsc --noEmit`, `eslint`, `vitest run`, `vite build`, a self-review, and fixes before moving on.

| # | Phase | Deliverables |
|---|---|---|
| 1 | Discovery & plan | This document, [api-integration.md](api-integration.md) ✅ |
| 2 | Foundation ✅ | Vite/TS/ESLint, Tailwind + shadcn, tokens + dark mode, folder structure, API client (correlation ID, problem normalization), query client, router skeleton, AppShell with role-aware nav, ErrorBoundary, Vitest + MSW setup, `frontend-setup.md`, `design-system.md`, `architecture.md` |
| 3 | Auth & access ✅ | Login, register (invitation), password reset, token store + single-flight/cross-tab refresh, boot refresh, logout, `/users/me`, profile (name, password, theme), RequireAuth/RequireRole, Unauthorized/NotFound; tests for guards, refresh and the login flow. The notifications menu moved to Phase 4, next to the request detail page it links to |
| 4 | Engineer ✅ | Notifications menu, Overview (engineer widgets), My requests (filters, sort, pagination), catalog, new-request wizard (3 schemas), draft edit, request detail (summary, decision, provisioning, audit timeline, cancel), inventory |
| 5 | Manager ✅ | Manager overview, pending approvals, approval history, decision panel with confirmations |
| 6 | Admin & auditor ✅ | Platform overview, all requests, provisioning jobs + retry, users (roles/manager/team/disable with If-Match), invitations (one-time token display), departments & teams, catalog & policy editors, audit log with filters |
| 7 | Quality ✅ | Responsive and a11y pass, loading/error/empty audit across pages, workflow tests (scenarios 1–9 from the brief), bundle review, `routing-and-navigation.md`, `testing.md`, README section |

## 7. Decisions

- Backend gaps G1 and G3–G8 were closed in the backend. G2 (team/department in the directory) and G9 (stats endpoint) are still open and don't block anything (see [api-integration.md §8](api-integration.md#8-backend-gaps)).
