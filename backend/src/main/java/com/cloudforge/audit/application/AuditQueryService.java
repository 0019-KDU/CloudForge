package com.cloudforge.audit.application;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.audit.domain.AuditEvent;
import com.cloudforge.audit.repository.AuditEventRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to the audit trail. Authorization is the caller's job: the global search is
 * role-protected at the controller, and per-entity history is exposed only through the owning
 * module after its own access check (for example a request's trail via the request module).
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    // Open-ended bounds instead of nullable parameters: PostgreSQL cannot infer the type of a null
    // timestamp parameter in "(:from is null or ...)".
    private static final Instant BEGINNING = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("9999-12-31T00:00:00Z");

    private final AuditEventRepository events;

    public AuditQueryService(AuditEventRepository events) {
        this.events = events;
    }

    public Page<AuditEvent> history(AuditEntityType entityType, UUID entityId, Pageable pageable) {
        return events.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(entityType, entityId, pageable);
    }

    public Page<AuditEvent> search(AuditEntityType entityType, UUID entityId, UUID actorId, AuditAction action,
            Instant from, Instant to, Pageable pageable) {
        return events.search(entityType, entityId, actorId, action, from == null ? BEGINNING : from,
                to == null ? END : to, pageable);
    }
}
