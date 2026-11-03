-- Append-only audit trail.

CREATE TABLE audit_event (
    id              UUID         PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    actor_type      VARCHAR(10)  NOT NULL,
    actor_id        UUID         REFERENCES app_user (id),
    action          VARCHAR(60)  NOT NULL,
    entity_type     VARCHAR(40)  NOT NULL,
    entity_id       UUID         NOT NULL,
    correlation_id  VARCHAR(64),
    details         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_audit_event_actor CHECK (
        (actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type = 'SYSTEM' AND actor_id IS NULL)),
    CONSTRAINT ck_audit_event_details_object CHECK (jsonb_typeof(details) = 'object')
);
-- History of one entity (for example a request's audit trail).
CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id, occurred_at);
-- Global listing and time-range queries for auditors.
CREATE INDEX ix_audit_event_occurred_at ON audit_event (occurred_at DESC);
-- "What did this user do".
CREATE INDEX ix_audit_event_actor ON audit_event (actor_id, occurred_at DESC);

-- The application has no update or delete path for audit events; this trigger makes the table
-- append-only for every client, including ad-hoc SQL through the application's database role.
-- In production, also grant the application role only INSERT and SELECT on this table.
CREATE FUNCTION audit_event_reject_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% rejected)', TG_OP USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_reject_modification();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_reject_modification();
