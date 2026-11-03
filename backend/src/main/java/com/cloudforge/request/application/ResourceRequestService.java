package com.cloudforge.request.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.authorization.RequestAccessPolicy;
import com.cloudforge.authorization.RequestListScope;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.CloudForgeException;
import com.cloudforge.common.error.FieldViolation;
import com.cloudforge.common.error.InvalidSpecificationException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.common.persistence.AdvisoryLocks;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.application.UserAdministrationService;
import com.cloudforge.identity.domain.User;
import com.cloudforge.request.domain.RequestEvents.RequestSubmitted;
import com.cloudforge.request.domain.RequestStatus;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.request.repository.ResourceRequestRepository;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService.RequestablePolicy;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Requester-side use cases: drafting, submitting, cancelling, viewing and listing requests.
 *
 * <p>The requester and every other actor always come from the authenticated {@link CurrentUser};
 * nothing in a request body can name a requester, approver or status. Write operations re-check
 * that the actor's account is still active, which closes the access-token lifetime window for
 * disabled users on everything that changes state.
 */
@Service
@Transactional(readOnly = true)
public class ResourceRequestService {

    /** Upper bound for a stored spec, so a draft cannot be used to store arbitrary large documents. */
    static final int MAX_SPEC_BYTES = 16 * 1024;

    private final ResourceRequestRepository requests;
    private final RequestAccessPolicy accessPolicy;
    private final UserAdministrationService users;
    private final ResourceCatalogService catalog;
    private final AdvisoryLocks locks;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ResourceRequestService(ResourceRequestRepository requests, RequestAccessPolicy accessPolicy,
            UserAdministrationService users, ResourceCatalogService catalog, AdvisoryLocks locks, AuditLog audit,
            ApplicationEventPublisher events, JsonMapper jsonMapper, Clock clock) {
        this.requests = requests;
        this.accessPolicy = accessPolicy;
        this.users = users;
        this.catalog = catalog;
        this.locks = locks;
        this.audit = audit;
        this.events = events;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public ResourceRequest create(CurrentUser user, ResourceTypeCode type, Environment environment, JsonNode spec,
            String justification) {
        accessPolicy.requireCanCreate(user);
        users.requireActiveUser(user.id());
        catalog.getEntry(type, false); // the type must exist and be enabled; full spec checks happen on submit
        ResourceRequest request = requests.save(new ResourceRequest(nextRequestNumber(), user.id(), type, environment,
                toStoredSpec(spec), justification));
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_CREATED, AuditEntityType.RESOURCE_REQUEST,
                request.getId(), Map.of("requestNumber", request.getRequestNumber(), "resourceType", type,
                        "environment", environment));
        return request;
    }

    @Transactional
    public ResourceRequest updateDraft(CurrentUser user, UUID requestId, Environment environment, JsonNode spec,
            String justification) {
        users.requireActiveUser(user.id());
        ResourceRequest request = lockedForUser(user, requestId);
        accessPolicy.requireOwner(user, request.participants());
        request.updateDraft(environment, toStoredSpec(spec), justification);
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_UPDATED, AuditEntityType.RESOURCE_REQUEST, requestId);
        return request;
    }

    /**
     * Submits a draft for approval. The approver is the requester's manager at this moment and is
     * stored on the request, so later reorganizations do not move pending requests.
     */
    @Transactional
    public ResourceRequest submit(CurrentUser user, UUID requestId) {
        User requester = users.requireActiveUser(user.id());
        ResourceRequest request = lockedForUser(user, requestId);
        accessPolicy.requireOwner(user, request.participants());

        UUID approverId = requester.getManagerId();
        if (approverId == null) {
            throw new BusinessRuleViolationException("REQUESTER_HAS_NO_MANAGER",
                    "You have no manager to approve this request. Ask an administrator to assign one.");
        }
        try {
            users.requireEligibleManager(approverId);
        } catch (CloudForgeException e) {
            throw new BusinessRuleViolationException("APPROVER_UNAVAILABLE",
                    "Your manager cannot currently approve requests. Ask an administrator to update your manager.");
        }

        RequestablePolicy policy = catalog.checkRequestable(request.getResourceType(), request.getEnvironment(),
                jsonMapper.readTree(request.getSpecJson()));
        enforceQuota(request, policy);

        request.submit(approverId, Instant.now(clock));
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_SUBMITTED, AuditEntityType.RESOURCE_REQUEST,
                requestId, Map.of("approverId", approverId));
        events.publishEvent(new RequestSubmitted(requestId, request.getRequestNumber(), request.getRequesterId(), approverId));
        return request;
    }

    @Transactional
    public ResourceRequest cancel(CurrentUser user, UUID requestId) {
        users.requireActiveUser(user.id());
        ResourceRequest request = lockedForUser(user, requestId);
        accessPolicy.requireOwner(user, request.participants());
        request.cancel();
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_CANCELLED, AuditEntityType.RESOURCE_REQUEST, requestId);
        return request;
    }

    public ResourceRequest get(CurrentUser user, UUID requestId) {
        ResourceRequest request = requests.findById(requestId).orElseThrow(ResourceRequestService::notFound);
        accessPolicy.requireView(user, request.participants());
        return request;
    }

    /**
     * @param statuses empty means all statuses
     * @param text     optional case-insensitive substring of the request number or justification
     */
    public Page<ResourceRequest> list(CurrentUser user, RequestListScope scope, Set<RequestStatus> statuses,
            ResourceTypeCode resourceType, Environment environment, String text, Pageable pageable) {
        accessPolicy.requireListScope(user, scope);
        UUID requesterId = scope == RequestListScope.MINE ? user.id() : null;
        UUID approverId = scope == RequestListScope.ASSIGNED ? user.id() : null;
        Set<RequestStatus> effectiveStatuses = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(RequestStatus.class) : statuses;
        return requests.search(requesterId, approverId, effectiveStatuses, resourceType, environment,
                likePattern(text), pageable);
    }

    /** Request counts per status (every status present, zero if none) for the same scopes as {@link #list}. */
    public Map<RequestStatus, Long> countByStatus(CurrentUser user, RequestListScope scope) {
        accessPolicy.requireListScope(user, scope);
        UUID requesterId = scope == RequestListScope.MINE ? user.id() : null;
        UUID approverId = scope == RequestListScope.ASSIGNED ? user.id() : null;
        Map<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) {
            counts.put(status, 0L);
        }
        requests.countByStatus(requesterId, approverId).forEach(row -> counts.put(row.getStatus(), row.getCount()));
        return counts;
    }

    /**
     * Deletes a draft. Only drafts can be deleted: once submitted, a request is part of the approval
     * history and can only be cancelled. The audit trail keeps the creation and deletion events.
     */
    @Transactional
    public void deleteDraft(CurrentUser user, UUID requestId) {
        users.requireActiveUser(user.id());
        ResourceRequest request = lockedForUser(user, requestId);
        accessPolicy.requireOwner(user, request.participants());
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new BusinessRuleViolationException("REQUEST_NOT_DELETABLE",
                    "Only draft requests can be deleted; cancel a submitted request instead.");
        }
        requests.delete(request);
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_DELETED, AuditEntityType.RESOURCE_REQUEST, requestId,
                Map.of("requestNumber", request.getRequestNumber()));
    }

    private static String likePattern(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        // '!' is the LIKE escape character (see ResourceRequestRepository#search): user input is literal.
        String escaped = text.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** For other modules acting on a request (approval, provisioning) inside their own transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceRequest lockForUpdate(UUID requestId) {
        return requests.findForUpdate(requestId).orElseThrow(ResourceRequestService::notFound);
    }

    public JsonNode spec(ResourceRequest request) {
        return jsonMapper.readTree(request.getSpecJson());
    }

    private void enforceQuota(ResourceRequest request, RequestablePolicy policy) {
        if (policy.maxActivePerUser() == null) {
            return;
        }
        // Serialize submissions of the same user so two concurrent submits cannot both pass the count.
        locks.lock("request-quota:" + request.getRequesterId());
        long active = requests.countByRequesterIdAndResourceTypeAndEnvironmentAndStatusIn(request.getRequesterId(),
                request.getResourceType(), request.getEnvironment(), RequestStatus.ACTIVE);
        if (active >= policy.maxActivePerUser()) {
            throw new BusinessRuleViolationException("QUOTA_EXCEEDED", "You already have " + active + " active "
                    + request.getResourceType() + " requests in " + request.getEnvironment()
                    + "; the limit is " + policy.maxActivePerUser() + ".");
        }
    }

    private ResourceRequest lockedForUser(CurrentUser user, UUID requestId) {
        ResourceRequest request = requests.findForUpdate(requestId).orElseThrow(ResourceRequestService::notFound);
        accessPolicy.requireView(user, request.participants());
        return request;
    }

    private String toStoredSpec(JsonNode spec) {
        if (spec == null || !spec.isObject()) {
            throw invalidSpec("must be a JSON object");
        }
        String json = jsonMapper.writeValueAsString(spec);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SPEC_BYTES) {
            throw invalidSpec("must not exceed " + MAX_SPEC_BYTES + " bytes");
        }
        return json;
    }

    private String nextRequestNumber() {
        int year = Instant.now(clock).atZone(ZoneOffset.UTC).getYear();
        return "CF-%d-%06d".formatted(year, requests.nextRequestNumber());
    }

    private static InvalidSpecificationException invalidSpec(String message) {
        return new InvalidSpecificationException("INVALID_RESOURCE_SPEC", "The resource specification is invalid.",
                List.of(new FieldViolation("spec", message)));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("REQUEST_NOT_FOUND", "Request was not found.");
    }
}
