# ADR-0004: RFC 9457 Problem Details as the single error format

**Status:** Accepted · 2026-10-30

## Decision
Every error, including Spring Security 401/403 raised outside MVC, is returned as `application/problem+json` with three extensions: `code` (stable and machine-readable), `correlationId` and `timestamp`. Domain exceptions carry a code. `GlobalExceptionHandler` maps exception *type* to status, so the domain never references HTTP. Unexpected exceptions return a generic 500. Validation errors list field names and messages but never the rejected values.

## Consequences
- Clients branch on `code`, not on message text.
- Spring Framework supports `ProblemDetail` natively, so no extra library is needed.
