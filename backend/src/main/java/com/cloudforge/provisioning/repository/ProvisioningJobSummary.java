package com.cloudforge.provisioning.repository;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.provisioning.domain.JobStatus;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

/** Read model for the job list: a job joined with its request number, loaded in one query. */
public record ProvisioningJobSummary(UUID jobId, UUID requestId, String requestNumber, ResourceTypeCode resourceType,
        JobStatus status, int attemptCount, Instant nextRunAt, String lastErrorCode, String lastErrorMessage,
        Instant createdAt, Instant updatedAt) {
}
