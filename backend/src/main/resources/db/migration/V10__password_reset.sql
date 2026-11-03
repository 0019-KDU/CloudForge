-- Administrator-issued password reset tokens. CloudForge has no email channel yet, so a reset works
-- like an invitation: an administrator issues a one-time token and hands it to the user, who redeems
-- it with a new password. Only the SHA-256 hash of the token is stored.

CREATE TABLE password_reset_token (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(64) NOT NULL,
    issued_by   UUID        NOT NULL REFERENCES app_user (id),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)
);
-- Invalidating a user's open tokens when a new one is issued.
CREATE INDEX ix_password_reset_token_user ON password_reset_token (user_id) WHERE used_at IS NULL;
CREATE INDEX ix_password_reset_token_issued_by ON password_reset_token (issued_by);

-- Free-text request search (request number and justification) uses ILIKE; a trigram index keeps
-- substring matches fast as the table grows.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_resource_request_search ON resource_request
    USING gin ((lower(request_number) || ' ' || lower(justification)) gin_trgm_ops);
