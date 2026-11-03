package com.cloudforge.approval.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.approval.domain.ApprovalRecord;
import com.cloudforge.approval.domain.ApprovalRecord.Decision;
import com.cloudforge.approval.repository.ApprovalRecordRepository;
import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.authorization.RequestAccessPolicy;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.application.UserAdministrationService;
import com.cloudforge.provisioning.application.ProvisioningJobService;
import com.cloudforge.request.application.ResourceRequestService;
import com.cloudforge.request.domain.RequestEvents.RequestDecided;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-manager approval. A decision is one transaction that locks the request row
 * ({@code SELECT ... FOR UPDATE}), checks the state machine and the decider, writes the immutable
 * {@link ApprovalRecord}, updates the request, writes the audit event and, on approval, queues
 * provisioning. Concurrent decisions or a decision racing a cancellation are serialized by the row
 * lock; the unique {@code approval_record.request_id} is the database backstop.
 */
@Service
@Transactional(readOnly = true)
public class ApprovalService {

    private final ResourceRequestService requests;
    private final ApprovalRecordRepository approvals;
    private final RequestAccessPolicy accessPolicy;
    private final UserAdministrationService users;
    private final ResourceCatalogService catalog;
    private final ProvisioningJobService provisioning;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalService(ResourceRequestService requests, ApprovalRecordRepository approvals,
            RequestAccessPolicy accessPolicy, UserAdministrationService users, ResourceCatalogService catalog,
            ProvisioningJobService provisioning, AuditLog audit, ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.approvals = approvals;
        this.accessPolicy = accessPolicy;
        this.users = users;
        this.catalog = catalog;
        this.provisioning = provisioning;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ApprovalRecord approve(CurrentUser user, UUID requestId, String comment) {
        ResourceRequest request = lockForDecision(user, requestId);
        // The policy may have been tightened since submission; approving must not bypass it.
        catalog.checkRequestable(request.getResourceType(), request.getEnvironment(), requests.spec(request));

        Instant now = Instant.now(clock);
        request.approve(now);
        ApprovalRecord record = approvals.save(new ApprovalRecord(requestId, user.id(), Decision.APPROVED, comment, now));
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_APPROVED, AuditEntityType.RESOURCE_REQUEST, requestId,
                details(record));
        provisioning.enqueue(request, Actor.user(user.id()));
        events.publishEvent(new RequestDecided(requestId, request.getRequestNumber(), request.getRequesterId(), true,
                record.getComment()));
        return record;
    }

    @Transactional
    public ApprovalRecord reject(CurrentUser user, UUID requestId, String comment) {
        ResourceRequest request = lockForDecision(user, requestId);
        Instant now = Instant.now(clock);
        request.reject(now);
        ApprovalRecord record = approvals.save(new ApprovalRecord(requestId, user.id(), Decision.REJECTED, comment, now));
        audit.record(Actor.user(user.id()), AuditAction.REQUEST_REJECTED, AuditEntityType.RESOURCE_REQUEST, requestId,
                details(record));
        events.publishEvent(new RequestDecided(requestId, request.getRequestNumber(), request.getRequesterId(), false,
                record.getComment()));
        return record;
    }

    public Optional<ApprovalRecord> decisionFor(CurrentUser user, UUID requestId) {
        requests.get(user, requestId);
        return approvals.findByRequestId(requestId);
    }

    private ResourceRequest lockForDecision(CurrentUser user, UUID requestId) {
        users.requireActiveUser(user.id());
        ResourceRequest request = requests.lockForUpdate(requestId);
        accessPolicy.requireDecider(user, request.participants());
        return request;
    }

    private static Map<String, Object> details(ApprovalRecord record) {
        Map<String, Object> details = new HashMap<>();
        details.put("approvalId", record.getId());
        if (record.getComment() != null) {
            details.put("comment", record.getComment());
        }
        return details;
    }
}
