package com.cloudforge.request.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.cloudforge.audit.api.AuditEventResponse;
import com.cloudforge.audit.application.AuditQueryService;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.authorization.RequestListScope;
import com.cloudforge.common.api.PageResponse;
import com.cloudforge.common.api.SortablePageRequest;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.request.api.RequestDtos.CreateRequest;
import com.cloudforge.request.api.RequestDtos.RequestResponse;
import com.cloudforge.request.api.RequestDtos.UpdateDraftRequest;
import com.cloudforge.request.application.ResourceRequestService;
import com.cloudforge.request.domain.RequestStatus;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.json.JsonMapper;

/** Requests are changed only through business operations; there is no generic update of status. */
@RestController
@RequestMapping("/api/requests")
class ResourceRequestController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "requestNumber", "status");

    private final ResourceRequestService requests;
    private final AuditQueryService audit;
    private final JsonMapper jsonMapper;

    ResourceRequestController(ResourceRequestService requests, AuditQueryService audit, JsonMapper jsonMapper) {
        this.requests = requests;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RequestResponse create(CurrentUser user, @Valid @RequestBody CreateRequest body) {
        return toResponse(requests.create(user, body.resourceType(), body.environment(), body.spec(), body.justification()));
    }

    @GetMapping
    PageResponse<RequestResponse> list(CurrentUser user,
            @RequestParam(defaultValue = "MINE") RequestListScope scope,
            @RequestParam(required = false) Set<RequestStatus> status,
            @RequestParam(required = false) ResourceTypeCode resourceType,
            @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // status may repeat (?status=APPROVED&status=PROVISIONING); q searches number and justification.
        return PageResponse.of(requests.list(user, scope, status, resourceType, environment, q,
                SortablePageRequest.restrict(pageable, SORTABLE)), this::toResponse);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDraft(CurrentUser user, @PathVariable UUID id) {
        requests.deleteDraft(user, id);
    }

    record SummaryResponse(RequestListScope scope, long total, Map<RequestStatus, Long> byStatus) {
    }

    /** Dashboard counters: {@code GET /api/requests/summary?scope=ASSIGNED} → counts per status. */
    @GetMapping("/summary")
    SummaryResponse summary(CurrentUser user, @RequestParam(defaultValue = "MINE") RequestListScope scope) {
        Map<RequestStatus, Long> counts = requests.countByStatus(user, scope);
        return new SummaryResponse(scope, counts.values().stream().mapToLong(Long::longValue).sum(), counts);
    }

    @GetMapping("/{id}")
    RequestResponse get(CurrentUser user, @PathVariable UUID id) {
        return toResponse(requests.get(user, id));
    }

    @PutMapping("/{id}")
    RequestResponse updateDraft(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateDraftRequest body) {
        return toResponse(requests.updateDraft(user, id, body.environment(), body.spec(), body.justification()));
    }

    @PostMapping("/{id}/submit")
    RequestResponse submit(CurrentUser user, @PathVariable UUID id) {
        return toResponse(requests.submit(user, id));
    }

    @PostMapping("/{id}/cancel")
    RequestResponse cancel(CurrentUser user, @PathVariable UUID id) {
        return toResponse(requests.cancel(user, id));
    }

    /** The request's audit trail, oldest first; visible to everyone who can see the request. */
    @GetMapping("/{id}/audit")
    PageResponse<AuditEventResponse> auditTrail(CurrentUser user, @PathVariable UUID id, Pageable pageable) {
        requests.get(user, id);
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.of(audit.history(AuditEntityType.RESOURCE_REQUEST, id, page),
                event -> AuditEventResponse.from(event, jsonMapper));
    }

    private RequestResponse toResponse(ResourceRequest request) {
        return RequestResponse.from(request, jsonMapper);
    }
}
