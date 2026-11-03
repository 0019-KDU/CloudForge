package com.cloudforge.request.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.cloudforge.authorization.RequestParticipants;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.persistence.BaseEntity;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A request for one cloud resource. All status changes are explicit business methods that enforce
 * the {@link RequestStatus} state machine; there is no status setter.
 *
 * <p>Concurrent changes are detected by the {@code version} column (optimistic locking), and
 * decisions additionally lock the row (see {@code ResourceRequestRepository#findForUpdate}).
 */
@Entity
@Table(name = "resource_request")
public class ResourceRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, length = 20, updatable = false)
    private String requestNumber;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "assigned_approver_id")
    private UUID assignedApproverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type_code", nullable = false, length = 40, updatable = false)
    private ResourceTypeCode resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spec", nullable = false)
    private String specJson;

    @Column(nullable = false, length = 2000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ResourceRequest() {
        // for JPA
    }

    public ResourceRequest(String requestNumber, UUID requesterId, ResourceTypeCode resourceType,
            Environment environment, String specJson, String justification) {
        this.requestNumber = Objects.requireNonNull(requestNumber, "requestNumber");
        this.requesterId = Objects.requireNonNull(requesterId, "requesterId");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.status = RequestStatus.DRAFT;
        updateDraft(environment, specJson, justification);
    }

    /** Drafts are freely editable by their owner; once submitted, the content is frozen. */
    public void updateDraft(Environment newEnvironment, String newSpecJson, String newJustification) {
        if (status != RequestStatus.DRAFT) {
            throw new BusinessRuleViolationException("REQUEST_NOT_EDITABLE", "Only draft requests can be edited.");
        }
        this.environment = Objects.requireNonNull(newEnvironment, "environment");
        this.specJson = Objects.requireNonNull(newSpecJson, "spec");
        this.justification = Objects.requireNonNull(newJustification, "justification").strip();
    }

    /** Captures the approver now; later changes to the requester's manager do not affect this request. */
    public void submit(UUID approverId, Instant now) {
        Objects.requireNonNull(approverId, "approverId");
        if (approverId.equals(requesterId)) {
            throw new BusinessRuleViolationException("SELF_APPROVAL_FORBIDDEN", "A request cannot be assigned to its requester.");
        }
        transitionTo(RequestStatus.PENDING_APPROVAL);
        this.assignedApproverId = approverId;
        this.submittedAt = now;
    }

    public void cancel() {
        transitionTo(RequestStatus.CANCELLED);
    }

    public void approve(Instant now) {
        transitionTo(RequestStatus.APPROVED);
        this.decidedAt = now;
    }

    public void reject(Instant now) {
        transitionTo(RequestStatus.REJECTED);
        this.decidedAt = now;
    }

    public void startProvisioning() {
        transitionTo(RequestStatus.PROVISIONING);
    }

    public void markProvisioned() {
        transitionTo(RequestStatus.PROVISIONED);
    }

    public void markProvisioningFailed() {
        transitionTo(RequestStatus.PROVISIONING_FAILED);
    }

    private void transitionTo(RequestStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleViolationException("INVALID_STATE_TRANSITION",
                    "A request in status " + status + " cannot move to " + target + ".");
        }
        this.status = target;
    }

    public RequestParticipants participants() {
        return new RequestParticipants(requesterId, assignedApproverId);
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getAssignedApproverId() {
        return assignedApproverId;
    }

    public ResourceTypeCode getResourceType() {
        return resourceType;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getSpecJson() {
        return specJson;
    }

    public String getJustification() {
        return justification;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
