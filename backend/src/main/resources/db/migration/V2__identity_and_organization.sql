-- Identity and organization: departments, teams, users, roles, manager hierarchy, invitations.

CREATE TABLE department (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL
);
CREATE UNIQUE INDEX uk_department_name ON department (lower(name));

CREATE TABLE team (
    id             UUID         PRIMARY KEY,
    department_id  UUID         NOT NULL REFERENCES department (id),
    name           VARCHAR(100) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL
);
-- Leads with department_id, so it also serves the FK and "teams of a department" lookups.
CREATE UNIQUE INDEX uk_team_department_name ON team (department_id, lower(name));

-- Fixed role catalogue. Roles are a Java enum; this table gives the database referential integrity.
CREATE TABLE role (
    code VARCHAR(30) PRIMARY KEY
);
INSERT INTO role (code) VALUES ('ENGINEER'), ('MANAGER'), ('PLATFORM_ADMIN'), ('AUDITOR');

CREATE TABLE app_user (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    manager_id     UUID         REFERENCES app_user (id),
    team_id        UUID         REFERENCES team (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email),
    -- Emails are normalized by the application; the check makes the unique constraint case-insensitive.
    CONSTRAINT ck_app_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_user_not_own_manager CHECK (manager_id IS NULL OR manager_id <> id)
);
CREATE INDEX ix_app_user_manager ON app_user (manager_id);
CREATE INDEX ix_app_user_team ON app_user (team_id);

CREATE TABLE user_role (
    user_id  UUID        NOT NULL REFERENCES app_user (id),
    role     VARCHAR(30) NOT NULL REFERENCES role (code),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE invitation (
    id           UUID         PRIMARY KEY,
    email        VARCHAR(254) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    manager_id   UUID         REFERENCES app_user (id),
    team_id      UUID         REFERENCES team (id),
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL,
    CONSTRAINT uk_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invitation_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);
-- At most one open invitation per email address.
CREATE UNIQUE INDEX uk_invitation_pending_email ON invitation (email) WHERE status = 'PENDING';
CREATE INDEX ix_invitation_manager ON invitation (manager_id);
CREATE INDEX ix_invitation_team ON invitation (team_id);
CREATE INDEX ix_invitation_created_at ON invitation (created_at DESC);

CREATE TABLE invitation_role (
    invitation_id  UUID        NOT NULL REFERENCES invitation (id),
    role           VARCHAR(30) NOT NULL REFERENCES role (code),
    PRIMARY KEY (invitation_id, role)
);
