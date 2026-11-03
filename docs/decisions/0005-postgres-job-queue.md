# ADR-0005: PostgreSQL-backed provisioning job queue

**Status:** Accepted · 2026-10-30

## Context
Provisioning calls take seconds to minutes (AWS account creation can take several minutes) and must not run inside HTTP requests. Jobs must survive restarts, must not run twice at the same time, and must be created atomically with the approval.

## Decision
Use a `provisioning_job` table. The approval transaction inserts the job. Workers on any instance claim jobs with `SELECT ... FOR UPDATE SKIP LOCKED` in a short transaction and set a time-bounded lease. Provider calls happen outside any transaction, and the results are written in a new short transaction. Retries use exponential backoff with jitter for retryable errors only. Long-running provider operations are polled through a persisted operation id.

## Consequences
- No new infrastructure. Atomic with business data. Survives crashes through lease expiry.
- Throughput is bounded by the database, which is fine for provisioning volumes (tens to thousands per day).
- Re-evaluate (a dedicated queue, or a workflow engine such as Temporal) if multi-step orchestration with compensation or very high volume appears.
