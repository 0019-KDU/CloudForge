package com.cloudforge.provisioning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.authorization.RequestAccessPolicy;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.application.UserAdministrationService;
import com.cloudforge.provisioning.domain.ProvisioningAttempt;
import com.cloudforge.provisioning.domain.ProvisioningJob;
import com.cloudforge.provisioning.domain.ProvisioningResult;
import com.cloudforge.provisioning.repository.ProvisioningAttemptRepository;
import com.cloudforge.provisioning.repository.ProvisioningJobRepository;
import com.cloudforge.provisioning.repository.ProvisioningResultRepository;
import com.cloudforge.request.application.ResourceRequestService;
import com.cloudforge.request.domain.ResourceRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Enqueueing (called by approval), administrator retry, and the provisioning status view. */
@Service
@Transactional(readOnly = true)
public class ProvisioningJobService {

    private final ProvisioningJobRepository jobs;
    private final ProvisioningAttemptRepository attempts;
    private final ProvisioningResultRepository results;
    private final ResourceRequestService requests;
    private final RequestAccessPolicy accessPolicy;
    private final UserAdministrationService users;
    private final AuditLog audit;
    private final Clock clock;

    public ProvisioningJobService(ProvisioningJobRepository jobs, ProvisioningAttemptRepository attempts,
            ProvisioningResultRepository results, ResourceRequestService requests, RequestAccessPolicy accessPolicy,
            UserAdministrationService users, AuditLog audit, Clock clock) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.results = results;
        this.requests = requests;
        this.accessPolicy = accessPolicy;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    public record ProvisioningStatus(ProvisioningJob job, List<ProvisioningAttempt> attempts,
            Optional<ProvisioningResult> result) {
    }

    /**
     * Queues provisioning for a just-approved request in the approver's transaction, so an approved
     * request always has a job and a rejected one never does.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProvisioningJob enqueue(ResourceRequest request, Actor actor) {
        ProvisioningJob job = jobs.save(new ProvisioningJob(request.getId(), request.getResourceType(), Instant.now(clock)));
        audit.record(actor, AuditAction.PROVISIONING_QUEUED, AuditEntityType.RESOURCE_REQUEST, request.getId());
        return job;
    }

    /** Administrator retry after a final failure: request back to PROVISIONING, job re-queued. */
    @Transactional
    public ProvisioningJob retry(CurrentUser user, UUID requestId) {
        users.requireActiveUser(user.id());
        ResourceRequest request = requests.lockForUpdate(requestId);
        accessPolicy.requireProvisioningOperator(user, request.participants());
        ProvisioningJob job = jobs.findByRequestIdForUpdate(requestId).orElseThrow(ProvisioningJobService::notFound);
        job.resetForRetry(Instant.now(clock));
        request.startProvisioning();
        audit.record(Actor.user(user.id()), AuditAction.PROVISIONING_RETRY_REQUESTED, AuditEntityType.RESOURCE_REQUEST,
                requestId);
        return job;
    }

    public ProvisioningStatus status(CurrentUser user, UUID requestId) {
        requests.get(user, requestId); // access check: 404 if the caller may not see the request
        ProvisioningJob job = jobs.findByRequestId(requestId).orElseThrow(ProvisioningJobService::notFound);
        return new ProvisioningStatus(job, attempts.findByJobIdOrderByStartedAtAsc(job.getId()),
                results.findByRequestId(requestId));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("PROVISIONING_NOT_FOUND", "This request has no provisioning job.");
    }
}
