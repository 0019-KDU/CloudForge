package com.cloudforge.request.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import com.cloudforge.request.domain.RequestStatus;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, UUID> {

    /** {@code SELECT ... FOR UPDATE}: serializes decisions, cancellation and provisioning on one request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResourceRequest r where r.id = :id")
    Optional<ResourceRequest> findForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT nextval('resource_request_number_seq')", nativeQuery = true)
    long nextRequestNumber();

    /**
     * One query for all list scopes. Exactly one of {@code requesterId} / {@code approverId} is set
     * for the MINE / ASSIGNED scopes, neither for ALL, so access filtering happens in SQL, not in memory.
     *
     * @param statuses    never empty; the caller passes all statuses when there is no status filter
     * @param textPattern lower-case LIKE pattern with {@code !} as escape; {@code %} matches everything.
     *                    The expression matches the trigram index {@code ix_resource_request_search}.
     */
    @Query("""
            select r from ResourceRequest r
            where (:requesterId is null or r.requesterId = :requesterId)
              and (:approverId is null or r.assignedApproverId = :approverId)
              and r.status in :statuses
              and (:resourceType is null or r.resourceType = :resourceType)
              and (:environment is null or r.environment = :environment)
              and concat(lower(r.requestNumber), ' ', lower(r.justification)) like :textPattern escape '!'
            """)
    Page<ResourceRequest> search(@Param("requesterId") UUID requesterId, @Param("approverId") UUID approverId,
            @Param("statuses") Collection<RequestStatus> statuses, @Param("resourceType") ResourceTypeCode resourceType,
            @Param("environment") Environment environment, @Param("textPattern") String textPattern,
            Pageable pageable);

    /** Counts per status for a list scope, in one GROUP BY query (dashboard counters). */
    @Query("""
            select r.status as status, count(r) as count from ResourceRequest r
            where (:requesterId is null or r.requesterId = :requesterId)
              and (:approverId is null or r.assignedApproverId = :approverId)
            group by r.status
            """)
    List<StatusCount> countByStatus(@Param("requesterId") UUID requesterId, @Param("approverId") UUID approverId);

    interface StatusCount {
        RequestStatus getStatus();

        long getCount();
    }

    long countByRequesterIdAndResourceTypeAndEnvironmentAndStatusIn(UUID requesterId, ResourceTypeCode resourceType,
            Environment environment, Collection<RequestStatus> statuses);
}
