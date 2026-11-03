package com.cloudforge.audit.api;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.audit.application.AuditQueryService;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.api.PageResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.json.JsonMapper;

/** Organization-wide audit search for auditors and platform administrators. Newest first. */
@RestController
@RequestMapping("/api/audit-events")
@PreAuthorize("hasAnyRole('AUDITOR', 'PLATFORM_ADMIN')")
class AuditController {

    private final AuditQueryService audit;
    private final JsonMapper jsonMapper;

    AuditController(AuditQueryService audit, JsonMapper jsonMapper) {
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping
    PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        // Ordering is fixed (newest first, indexed); client sort parameters are ignored.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.of(audit.search(entityType, entityId, actorId, action, from, to, page),
                event -> AuditEventResponse.from(event, jsonMapper));
    }
}
