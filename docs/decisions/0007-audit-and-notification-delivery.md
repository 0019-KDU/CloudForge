# ADR-0007: Audit in the business transaction, notifications after commit

**Status:** Accepted · 2026-10-30

## Context
Every important business action must be auditable, and the audit trail must never disagree with the data: no change without an entry, and no entry for a change that rolled back. Notifications matter less: losing one occasionally is tolerable, but a notification failure must never undo an approval.

## Decision
- **Audit:** application services call `AuditLog.record(...)` directly. It runs with `Propagation.MANDATORY`, so it fails fast outside a transaction and otherwise joins the caller's. The `audit` module depends on no other module, so calling it from everywhere creates no dependency cycle. The trail is append-only at three levels: `@Immutable` entity, a repository with no update or delete methods, and a PostgreSQL trigger rejecting UPDATE, DELETE and TRUNCATE.
- **Notifications:** services publish domain events (`RequestSubmitted`, `RequestDecided`, `ProvisioningFinished`). `NotificationListener` handles them with `@TransactionalEventListener(AFTER_COMMIT)` in a new transaction and swallows and logs its own failures, because an exception from an after-commit listener would reach the API caller as an error for a change that succeeded.
- **Rejected alternatives:** audit via a `BEFORE_COMMIT` event listener (works, but hides a must-have side effect behind indirection), and a transactional outbox for in-app notifications (more machinery than needed while every consumer is in-process).

## Consequences
- Audit cannot be lost or diverge from business data. Each write adds one INSERT to the transaction.
- A crash between commit and the notification listener loses that in-app notification; the request status and audit trail still show the change.
- Delivering notifications to external systems (email, chat) with guarantees will need a transactional outbox (see provisioning-design.md#outbox).
