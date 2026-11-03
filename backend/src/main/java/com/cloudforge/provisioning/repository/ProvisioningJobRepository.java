package com.cloudforge.provisioning.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import com.cloudforge.provisioning.domain.JobStatus;
import com.cloudforge.provisioning.domain.ProvisioningJob;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {

    Optional<ProvisioningJob> findByRequestId(UUID requestId);

    /** Operations view, most recently changed first; one query including the request number. */
    @Query(value = """
            select new com.cloudforge.provisioning.repository.ProvisioningJobSummary(
                j.id, j.requestId, r.requestNumber, j.resourceType, j.status, j.attemptCount, j.nextRunAt,
                j.lastErrorCode, j.lastErrorMessage, j.createdAt, j.updatedAt)
            from ProvisioningJob j join ResourceRequest r on r.id = j.requestId
            where j.status in :statuses and (:resourceType is null or j.resourceType = :resourceType)
            order by j.updatedAt desc
            """,
            countQuery = """
            select count(j) from ProvisioningJob j
            where j.status in :statuses and (:resourceType is null or j.resourceType = :resourceType)
            """)
    Page<ProvisioningJobSummary> summaries(@Param("statuses") Collection<JobStatus> statuses,
            @Param("resourceType") ResourceTypeCode resourceType, Pageable pageable);

    @Query("select j.status as status, count(j) as count from ProvisioningJob j group by j.status")
    List<JobStatusCount> countByStatus();

    interface JobStatusCount {
        JobStatus getStatus();

        long getCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProvisioningJob j where j.id = :id")
    Optional<ProvisioningJob> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProvisioningJob j where j.requestId = :requestId")
    Optional<ProvisioningJob> findByRequestIdForUpdate(@Param("requestId") UUID requestId);
}
