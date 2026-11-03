package com.cloudforge.provisioning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.persistence.BaseEntity;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

/**
 * A durable unit of provisioning work for one approved request.
 *
 * <p>Claiming (QUEUED/AWAITING_PROVIDER → RUNNING with a lease) is done in SQL by
 * {@code ProvisioningJobClaimer} with {@code FOR UPDATE SKIP LOCKED}; every other transition is a
 * method here, and each one that leaves RUNNING releases the lease.
 */
@Entity
@Table(name = "provisioning_job")
public class ProvisioningJob extends BaseEntity {

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type_code", nullable = false, length = 40, updatable = false)
    private ResourceTypeCode resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "provider_operation_id", length = 500)
    private String providerOperationId;

    @Column(name = "operation_started_at")
    private Instant operationStartedAt;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    protected ProvisioningJob() {
        // for JPA
    }

    public ProvisioningJob(UUID requestId, ResourceTypeCode resourceType, Instant now) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.idempotencyKey = "cloudforge-" + requestId;
        this.status = JobStatus.QUEUED;
        this.nextRunAt = now;
    }

    public boolean isLeasedBy(String workerId) {
        return status == JobStatus.RUNNING && workerId.equals(lockedBy);
    }

    /** Counts a start call. Polls of an accepted operation do not use up attempts. */
    public void countStartAttempt() {
        attemptCount++;
    }

    public void awaitProvider(String operationId, Instant nextPollAt, Instant now) {
        if (operationStartedAt == null || !Objects.equals(providerOperationId, operationId)) {
            operationStartedAt = now;
        }
        providerOperationId = Objects.requireNonNull(operationId, "operationId");
        status = JobStatus.AWAITING_PROVIDER;
        nextRunAt = nextPollAt;
        releaseLease();
    }

    /** Back to the queue after a retryable failure; a pending operation ID is kept so polling resumes. */
    public void scheduleRetry(Instant runAt, String errorCode, String errorMessage) {
        status = providerOperationId == null ? JobStatus.QUEUED : JobStatus.AWAITING_PROVIDER;
        nextRunAt = runAt;
        recordError(errorCode, errorMessage);
        releaseLease();
    }

    public void succeed() {
        status = JobStatus.SUCCEEDED;
        lastErrorCode = null;
        lastErrorMessage = null;
        releaseLease();
    }

    public void fail(String errorCode, String errorMessage) {
        status = JobStatus.FAILED;
        recordError(errorCode, errorMessage);
        releaseLease();
    }

    /**
     * Administrator retry of a failed job: a fresh attempt budget and a fresh start. The provider
     * operation ID is cleared; adapters find any resource created earlier through the idempotency key.
     */
    public void resetForRetry(Instant now) {
        if (status != JobStatus.FAILED) {
            throw new BusinessRuleViolationException("PROVISIONING_NOT_FAILED", "Only failed provisioning can be retried.");
        }
        status = JobStatus.QUEUED;
        attemptCount = 0;
        nextRunAt = now;
        providerOperationId = null;
        operationStartedAt = null;
    }

    private void recordError(String errorCode, String errorMessage) {
        lastErrorCode = errorCode;
        lastErrorMessage = SafeText.truncate(errorMessage, 1000);
    }

    private void releaseLease() {
        lockedBy = null;
        lockedUntil = null;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public ResourceTypeCode getResourceType() {
        return resourceType;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderOperationId() {
        return providerOperationId;
    }

    public Instant getOperationStartedAt() {
        return operationStartedAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
