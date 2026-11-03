-- Authentication: login throttling state, refresh tokens, invitation provenance.

-- Per-account brute-force protection (see AuthenticationService).
ALTER TABLE app_user
    ADD COLUMN failed_login_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMPTZ,
    ADD CONSTRAINT ck_app_user_failed_login_attempts CHECK (failed_login_attempts >= 0);

-- Who issued the invitation. Nullable: invitations created before this migration have no recorded issuer.
ALTER TABLE invitation
    ADD COLUMN invited_by UUID REFERENCES app_user (id);
CREATE INDEX ix_invitation_invited_by ON invitation (invited_by);

-- Opaque refresh tokens, stored as SHA-256 hashes. Every login starts a family; each refresh rotates
-- the token within the family. Presenting an already rotated token revokes the whole family
-- (refresh-token reuse detection).
CREATE TABLE refresh_token (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_user (id),
    family_id    UUID        NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    rotated_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT      NOT NULL,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);
