package com.cloudforge.audit.api;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.audit.domain.AuditEvent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Public JSON shape of an audit event; also used by other modules' audit-trail endpoints. */
public record AuditEventResponse(UUID id, Instant occurredAt, Actor.ActorType actorType, UUID actorId,
        AuditAction action, AuditEntityType entityType, UUID entityId, String correlationId, JsonNode details) {

    public static AuditEventResponse from(AuditEvent event, JsonMapper jsonMapper) {
        return new AuditEventResponse(event.getId(), event.getOccurredAt(), event.getActorType(), event.getActorId(),
                event.getAction(), event.getEntityType(), event.getEntityId(), event.getCorrelationId(),
                jsonMapper.readTree(event.getDetailsJson()));
    }
}
