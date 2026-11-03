# ADR-0002: Flyway owns the schema; Hibernate validates only

**Status:** Accepted · 2026-10-30

## Context
Hibernate's `ddl-auto=update` cannot express constraints, partial indexes, data backfills or safe renames, and it behaves differently on each startup.

## Decision
Every schema change is a versioned Flyway SQL migration. Hibernate runs with `ddl-auto=validate` in all profiles, so a mismatched entity fails at startup. Merged migrations are immutable. `flyway clean` stays disabled (the Flyway default).

## Consequences
- The SQL is reviewable, and constraints and indexes are explicit.
- Integration tests run the real migrations against PostgreSQL (Testcontainers), not H2, so PostgreSQL-specific features (JSONB, `SKIP LOCKED`, partial indexes) are tested for real.
