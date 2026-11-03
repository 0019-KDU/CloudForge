-- Per-client rate limit for authentication endpoints (fixed window). Stored in PostgreSQL rather than
-- in memory so the limit holds across all application instances.

CREATE TABLE login_throttle (
    client_key    VARCHAR(100) PRIMARY KEY,
    window_start  TIMESTAMPTZ  NOT NULL,
    attempts      INTEGER      NOT NULL,
    CONSTRAINT ck_login_throttle_attempts CHECK (attempts > 0)
);
-- Cleanup of stale windows.
CREATE INDEX ix_login_throttle_window_start ON login_throttle (window_start);
