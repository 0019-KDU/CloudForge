package com.cloudforge.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** An in-app message for one user. Only {@code readAt} ever changes. */
@Entity
@Table(name = "notification")
public class Notification {

    public enum Type {
        APPROVAL_REQUESTED,
        REQUEST_APPROVED,
        REQUEST_REJECTED,
        PROVISIONING_SUCCEEDED,
        PROVISIONING_FAILED
    }

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private Type type;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public Notification(UUID recipientId, Type type, String title, String body, UUID requestId, Instant createdAt) {
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.type = Objects.requireNonNull(type, "type");
        this.title = truncate(Objects.requireNonNull(title, "title"), 200);
        this.body = truncate(Objects.requireNonNull(body, "body"), 1000);
        this.requestId = requestId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
