package com.cloudforge.audit.repository;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.audit.domain.AuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately a plain {@link Repository}, not {@code JpaRepository}: it exposes insert and read
 * methods only, so no delete or bulk-update method exists anywhere in the code.
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(AuditEntityType entityType, UUID entityId,
            Pageable pageable);

    @Query("""
            select e from AuditEvent e
            where (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
              and (:actorId is null or e.actorId = :actorId)
              and (:action is null or e.action = :action)
              and e.occurredAt >= :from
              and e.occurredAt < :to
            order by e.occurredAt desc
            """)
    Page<AuditEvent> search(@Param("entityType") AuditEntityType entityType, @Param("entityId") UUID entityId,
            @Param("actorId") UUID actorId, @Param("action") AuditAction action,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
