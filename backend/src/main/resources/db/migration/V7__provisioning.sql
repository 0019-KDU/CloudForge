-- Provisioning job queue (ADR-0005), execution history and results.

CREATE TABLE provisioning_job (
    id                     UUID          PRIMARY KEY,
    request_id             UUID          NOT NULL REFERENCES resource_request (id),
    resource_type_code     VARCHAR(40)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    attempt_count          INTEGER       NOT NULL DEFAULT 0,
    next_run_at            TIMESTAMPTZ   NOT NULL,
    -- Lease: which worker holds the job and until when. An expired lease makes the job claimable
    -- again, which recovers jobs from crashed instances.
    locked_by              VARCHAR(100),
    locked_until           TIMESTAMPTZ,
    -- Stable per request; adapters pass it to providers (labels, tags) to detect earlier attempts.
    idempotency_key        VARCHAR(100)  NOT NULL,
    -- Provider handle of a long-running operation (GCP operation name, AWS CreateAccount request id).
    provider_operation_id  VARCHAR(500),
    operation_started_at   TIMESTAMPTZ,
    last_error_code        VARCHAR(60),
    last_error_message     VARCHAR(1000),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT uk_provisioning_job_request UNIQUE (request_id),
    CONSTRAINT uk_provisioning_job_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_provisioning_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'AWAITING_PROVIDER', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_provisioning_job_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_provisioning_job_lease CHECK ((status = 'RUNNING') = (locked_by IS NOT NULL AND locked_until IS NOT NULL))
);
-- Worker claim query: due jobs only (partial index stays small as finished jobs accumulate).
CREATE INDEX ix_provisioning_job_due ON provisioning_job (next_run_at) WHERE status IN ('QUEUED', 'AWAITING_PROVIDER');
-- Recovery of expired leases.
CREATE INDEX ix_provisioning_job_lease ON provisioning_job (locked_until) WHERE status = 'RUNNING';

CREATE TABLE provisioning_attempt (
    id                     UUID          PRIMARY KEY,
    job_id                 UUID          NOT NULL REFERENCES provisioning_job (id),
    attempt_number         INTEGER       NOT NULL,
    kind                   VARCHAR(10)   NOT NULL,
    worker_id              VARCHAR(100)  NOT NULL,
    started_at             TIMESTAMPTZ   NOT NULL,
    finished_at            TIMESTAMPTZ   NOT NULL,
    outcome                VARCHAR(20)   NOT NULL,
    error_code             VARCHAR(60),
    -- Sanitized: never credentials, tokens or raw provider payloads.
    error_message          VARCHAR(1000),
    provider_operation_id  VARCHAR(500),
    CONSTRAINT ck_provisioning_attempt_kind CHECK (kind IN ('START', 'POLL')),
    CONSTRAINT ck_provisioning_attempt_outcome CHECK (outcome IN ('SUCCEEDED', 'IN_PROGRESS', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE'))
);
CREATE INDEX ix_provisioning_attempt_job ON provisioning_attempt (job_id, started_at);

CREATE TABLE provisioning_result (
    id                    UUID          PRIMARY KEY,
    job_id                UUID          NOT NULL REFERENCES provisioning_job (id),
    request_id            UUID          NOT NULL REFERENCES resource_request (id),
    external_resource_id  VARCHAR(255)  NOT NULL,
    details               JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_provisioning_result_job UNIQUE (job_id),
    CONSTRAINT uk_provisioning_result_request UNIQUE (request_id),
    CONSTRAINT ck_provisioning_result_details_object CHECK (jsonb_typeof(details) = 'object')
);
