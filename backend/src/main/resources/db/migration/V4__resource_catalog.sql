-- Resource catalog: the provisionable resource types and their per-environment policies.

CREATE TABLE resource_type (
    id            UUID         PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL,
    provider      VARCHAR(20)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    description   VARCHAR(1000) NOT NULL,
    enabled       BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL,
    CONSTRAINT uk_resource_type_code UNIQUE (code),
    CONSTRAINT ck_resource_type_provider CHECK (provider IN ('AWS', 'GCP', 'KUBERNETES'))
);

-- Types are defined in code (each needs its own validator and provisioning adapter), so they are
-- seeded here rather than created through the API. Admins can rename, describe and enable them.
INSERT INTO resource_type (id, code, provider, display_name, description, enabled, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'AWS_ACCOUNT', 'AWS', 'AWS account',
     'A new member account in the AWS Organization, placed in an approved organizational unit.',
     TRUE, now(), now(), 0),
    (gen_random_uuid(), 'GCP_PROJECT', 'GCP', 'GCP project',
     'A new Google Cloud project created under an approved folder.',
     TRUE, now(), now(), 0),
    (gen_random_uuid(), 'K8S_NAMESPACE', 'KUBERNETES', 'Kubernetes namespace',
     'A namespace on the shared cluster with a CPU and memory quota.',
     TRUE, now(), now(), 0);

-- One policy per (type, environment). A missing row or allowed = false means the type cannot be
-- requested in that environment: deny by default.
CREATE TABLE resource_policy (
    id                   UUID        PRIMARY KEY,
    resource_type_id     UUID        NOT NULL REFERENCES resource_type (id),
    environment          VARCHAR(20) NOT NULL,
    allowed              BOOLEAN     NOT NULL,
    max_active_per_user  INTEGER,
    constraints          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL,
    CONSTRAINT uk_resource_policy_type_environment UNIQUE (resource_type_id, environment),
    CONSTRAINT ck_resource_policy_environment CHECK (environment IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION')),
    CONSTRAINT ck_resource_policy_max_active CHECK (max_active_per_user IS NULL OR max_active_per_user > 0),
    CONSTRAINT ck_resource_policy_constraints_object CHECK (jsonb_typeof(constraints) = 'object')
);
