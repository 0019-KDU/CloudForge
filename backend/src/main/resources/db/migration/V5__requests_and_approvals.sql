-- Resource requests and single-manager approval decisions.

-- Human-readable request numbers (CF-<year>-<000123>). A sequence never hands out the same value
-- twice, even across concurrent transactions; gaps after rollbacks are acceptable.
CREATE SEQUENCE resource_request_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE resource_request (
    id                    UUID          PRIMARY KEY,
    request_number        VARCHAR(20)   NOT NULL,
    requester_id          UUID          NOT NULL REFERENCES app_user (id),
    -- Captured at submission and never changed, even if the requester's manager changes later.
    assigned_approver_id  UUID          REFERENCES app_user (id),
    resource_type_code    VARCHAR(40)   NOT NULL REFERENCES resource_type (code),
    environment           VARCHAR(20)   NOT NULL,
    spec                  JSONB         NOT NULL,
    justification         VARCHAR(2000) NOT NULL,
    status                VARCHAR(30)   NOT NULL,
    submitted_at          TIMESTAMPTZ,
    decided_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL,
    CONSTRAINT uk_resource_request_number UNIQUE (request_number),
    CONSTRAINT ck_resource_request_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
        'CANCELLED', 'PROVISIONING', 'PROVISIONED', 'PROVISIONING_FAILED')),
    CONSTRAINT ck_resource_request_environment CHECK (environment IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION')),
    CONSTRAINT ck_resource_request_spec_object CHECK (jsonb_typeof(spec) = 'object'),
    -- Database backstop for "users cannot approve their own requests".
    CONSTRAINT ck_resource_request_not_self_approver CHECK (assigned_approver_id IS NULL OR assigned_approver_id <> requester_id),
    -- Anything past DRAFT must have an approver and a submission time.
    CONSTRAINT ck_resource_request_submitted CHECK (status = 'DRAFT' OR (assigned_approver_id IS NOT NULL AND submitted_at IS NOT NULL))
);
-- "My requests", newest first.
CREATE INDEX ix_resource_request_requester ON resource_request (requester_id, created_at DESC);
-- Manager inbox: requests assigned to me in a given status, oldest submission first.
CREATE INDEX ix_resource_request_approver ON resource_request (assigned_approver_id, status, submitted_at);
-- Admin and auditor listing by date.
CREATE INDEX ix_resource_request_created_at ON resource_request (created_at DESC);
-- Quota check: active requests of a user for a type and environment.
CREATE INDEX ix_resource_request_quota ON resource_request (requester_id, resource_type_code, environment, status);

CREATE TABLE approval_record (
    id           UUID          PRIMARY KEY,
    request_id   UUID          NOT NULL REFERENCES resource_request (id),
    approver_id  UUID          NOT NULL REFERENCES app_user (id),
    decision     VARCHAR(20)   NOT NULL,
    comment      VARCHAR(2000),
    decided_at   TIMESTAMPTZ   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    -- Single-approver model: one decision per request. Also the backstop against double approval.
    CONSTRAINT uk_approval_record_request UNIQUE (request_id),
    CONSTRAINT ck_approval_record_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_approval_record_reject_comment CHECK (decision <> 'REJECTED' OR (comment IS NOT NULL AND length(trim(comment)) > 0))
);
CREATE INDEX ix_approval_record_approver ON approval_record (approver_id, decided_at DESC);
