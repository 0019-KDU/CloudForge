-- In-app notifications.

CREATE TABLE notification (
    id            UUID          PRIMARY KEY,
    recipient_id  UUID          NOT NULL REFERENCES app_user (id),
    type          VARCHAR(40)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    body          VARCHAR(1000) NOT NULL,
    request_id    UUID          REFERENCES resource_request (id),
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL
);
-- "My notifications", newest first, and the unread count.
CREATE INDEX ix_notification_recipient ON notification (recipient_id, created_at DESC);
CREATE INDEX ix_notification_unread ON notification (recipient_id) WHERE read_at IS NULL;
CREATE INDEX ix_notification_request ON notification (request_id);
