package com.cloudforge.audit.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.audit.domain.AuditEvent;
import com.cloudforge.audit.repository.AuditEventRepository;
import com.cloudforge.common.web.CorrelationIdFilter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Records audit events inside the caller's transaction ({@code MANDATORY}): the audit row commits
 * if and only if the business change commits, so there is never a change without its audit entry,
 * nor an entry for a change that was rolled back.
 *
 * <p>Callers must keep secrets out of {@code details}: no passwords, tokens or credentials.
 */
@Service
public class AuditLog {

    private final AuditEventRepository events;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditLog(AuditEventRepository events, JsonMapper jsonMapper, Clock clock) {
        this.events = events;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, AuditAction action, AuditEntityType entityType, UUID entityId,
            Map<String, ?> details) {
        events.save(new AuditEvent(Instant.now(clock), actor, action, entityType, entityId,
                CorrelationIdFilter.current(), jsonMapper.writeValueAsString(details == null ? Map.of() : details)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, AuditAction action, AuditEntityType entityType, UUID entityId) {
        record(actor, action, entityType, entityId, Map.of());
    }
}
