package com.cloudforge.audit.domain;

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

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One immutable audit record. {@code @Immutable} makes Hibernate ignore changes to loaded instances,
 * and a database trigger rejects UPDATE and DELETE, so history cannot be rewritten.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private Actor.ActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String detailsJson;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(Instant occurredAt, Actor actor, AuditAction action, AuditEntityType entityType, UUID entityId,
            String correlationId, String detailsJson) {
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.actorType = actor.type();
        this.actorId = actor.userId();
        this.action = Objects.requireNonNull(action, "action");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.correlationId = correlationId;
        this.detailsJson = Objects.requireNonNull(detailsJson, "detailsJson");
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Actor.ActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }
}
