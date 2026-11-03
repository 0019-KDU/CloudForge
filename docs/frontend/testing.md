# Frontend testing

```bash
cd frontend
npm run test:run     # once
npm test             # watch mode
npm run check        # typecheck + lint + tests + build (run at the end of every phase)
```

Stack: Vitest (jsdom), React Testing Library, user-event, MSW 2.

## Approach

- **Render the real app.** `test/render.tsx` mounts the actual route tree, providers and query client
  at a URL. Tests click through pages the way a user does.
- **Mock at the network boundary only.** MSW intercepts `/api/**`. Nothing inside the app is mocked.
- **Accessible selectors.** Roles, labels and visible text (`getByRole("button", { name: "Approve" })`).
  A test that can't find an element by role or label usually points to an accessibility bug; two were
  found this way (missing `h1` on auth pages, card titles that weren't headings).
- **Unhandled requests fail.** The MSW server uses `onUnhandledRequest: "error"`.

## Test infrastructure

| File | Purpose |
|---|---|
| `test/setup.ts` | jest-dom matchers, jsdom shims for Radix (ResizeObserver, pointer capture, matchMedia), MSW lifecycle, auth state reset between tests |
| `test/server.ts` | MSW server with the anonymous defaults; `signedInAs(user)` for simple cases |
| `test/fixtures.ts` | Backend-shaped users, tokens and problem documents |
| `test/fake-backend.ts` | A small **stateful** fake of the CloudForge API for workflow tests: request scopes, status transitions, approver checks, 404 masking, 403/422 codes, decisions, provisioning jobs, audit trail. It records every call so tests can assert the exact request sent |

The fake follows the backend contract but is not the backend: business rules are tested in the
backend's own suite. The frontend contract was also checked once against the running backend
(Testcontainers database, mock providers) with a script that sends the same bodies the UI sends.

## What is covered

| Scenario (from the brief) | Tests |
|---|---|
| 1. Engineer logs in | `auth-flow.test.tsx`: redirect to login and back, validation, wrong credentials, rate limiting, session resume, logout, expiry |
| 2. Engineer creates a request | `request-workflow.test.tsx`: full wizard with exact request body; per-type/per-environment validation; server field errors on the right input; draft kept when submit is refused; types with no environment disabled |
| 3. Engineer views status | `request-workflow.test.tsx`: filtered list (`scope=MINE&status=`), failure reason, created resource, cancel with confirmation |
| 4. Manager sees assigned approvals | `approval-workflow.test.tsx`: `scope=ASSIGNED&status=PENDING_APPROVAL`, requester names |
| 5. Manager approves or rejects | `approval-workflow.test.tsx`: confirm dialog, one call only, comment sent; reject needs a reason; stale request refused with the server's message |
| 6. Engineer sees updated status | detail page re-renders from the server after mutations (approve, cancel, retry) |
| 7. Provisioning status | failed job with reason, succeeded job with result, admin-only retry |
| 8. Restricted routes | `role-access.test.tsx`, `access.test.tsx`: navigation per role, blocked routes per role, anonymous redirect, safe `next` |
| 9. API errors | `problem.test.ts`: problem documents, 5xx hiding, Retry-After, non-JSON errors, network errors. `session.test.ts`: single-flight refresh, 401 replay, expiry |
| Admin concurrency | `admin-users.test.tsx`: `If-Match` with the loaded version, 412 reported instead of overwriting |
| Validation rules | `spec.test.ts`, `permissions.test.ts`, `profile.test.tsx` |

## Notes

- Route modules load lazily, so the first `findBy*` in a test can take a moment: the async timeout is
  5 s and the test timeout 20 s.
- `navigator.locks` doesn't exist in jsdom; the refresh code falls back to the in-tab promise, which is
  what the single-flight test covers. Cross-tab locking is verified manually in a browser.
- There are no browser end-to-end tests (Playwright) yet. They would be the next step for layout,
  focus management and cross-tab behaviour.
