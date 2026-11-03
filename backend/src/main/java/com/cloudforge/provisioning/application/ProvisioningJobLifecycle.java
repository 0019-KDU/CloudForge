package com.cloudforge.provisioning.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.CloudForgeException;
import com.cloudforge.provisioning.ProvisioningProperties;
import com.cloudforge.provisioning.domain.ProvisioningAttempt;
import com.cloudforge.provisioning.domain.ProvisioningEvents.ProvisioningFinished;
import com.cloudforge.provisioning.domain.ProvisioningJob;
import com.cloudforge.provisioning.domain.ProvisioningResult;
import com.cloudforge.provisioning.domain.RetryPolicy;
import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Completed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Failed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.InProgress;
import com.cloudforge.provisioning.repository.ProvisioningAttemptRepository;
import com.cloudforge.provisioning.repository.ProvisioningJobRepository;
import com.cloudforge.provisioning.repository.ProvisioningResultRepository;
import com.cloudforge.request.application.ResourceRequestService;
import com.cloudforge.request.domain.RequestStatus;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * The two short transactions around each provider call. The provider call itself happens between
 * them, outside any transaction, so no database connection or row lock is held while waiting on a
 * cloud API.
 *
 * <ol>
 *   <li>{@link #begin}: verify the lease, move the request to PROVISIONING, re-check the spec against
 *       the current policy, and build the command.</li>
 *   <li>{@link #record}: verify the lease is still ours, store the attempt, and apply the outcome to the
 *       job, the request, the audit trail and the result.</li>
 * </ol>
 */
@Component
class ProvisioningJobLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningJobLifecycle.class);
    private static final Duration MIN_POLL = Duration.ofSeconds(5);
    private static final Duration MAX_POLL = Duration.ofMinutes(5);

    private final ProvisioningJobRepository jobs;
    private final ProvisioningAttemptRepository attempts;
    private final ProvisioningResultRepository results;
    private final ResourceRequestService requests;
    private final ResourceCatalogService catalog;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final RetryPolicy retryPolicy;
    private final ProvisioningProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    ProvisioningJobLifecycle(ProvisioningJobRepository jobs, ProvisioningAttemptRepository attempts, ProvisioningResultRepository results,
            ResourceRequestService requests, ResourceCatalogService catalog, AuditLog audit,
            ApplicationEventPublisher events, RetryPolicy retryPolicy, ProvisioningProperties properties,
            JsonMapper jsonMapper, Clock clock) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.results = results;
        this.requests = requests;
        this.catalog = catalog;
        this.audit = audit;
        this.events = events;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** What the worker should do with the provider: start, or poll an existing operation. */
    record Execution(UUID jobId, ProvisioningCommand command, ProvisioningAttempt.Kind kind, String operationId,
            int attemptNumber) {
    }

    @Transactional
    public Optional<Execution> begin(UUID jobId, String workerId) {
        ProvisioningJob job = jobs.findForUpdate(jobId).orElse(null);
        if (job == null || !job.isLeasedBy(workerId)) {
            return Optional.empty();
        }
        ResourceRequest request = requests.lockForUpdate(job.getRequestId());

        // Defence in depth: only approved requests are ever provisioned, whatever state the queue is in.
        if (request.getStatus() != RequestStatus.APPROVED && request.getStatus() != RequestStatus.PROVISIONING) {
            job.fail("REQUEST_NOT_PROVISIONABLE", "The request is " + request.getStatus() + " and cannot be provisioned.");
            log.warn("Discarded provisioning job {} for request in status {}", jobId, request.getStatus());
            return Optional.empty();
        }
        if (request.getStatus() == RequestStatus.APPROVED) {
            request.startProvisioning();
            audit.record(Actor.system(), AuditAction.PROVISIONING_STARTED, AuditEntityType.RESOURCE_REQUEST, request.getId());
        }

        ProvisioningAttempt.Kind kind = job.getProviderOperationId() == null
                ? ProvisioningAttempt.Kind.START : ProvisioningAttempt.Kind.POLL;
        if (kind == ProvisioningAttempt.Kind.START) {
            // The policy may have been tightened since approval; never create what is no longer allowed.
            try {
                catalog.checkRequestable(request.getResourceType(), request.getEnvironment(), requests.spec(request));
            } catch (CloudForgeException e) {
                failPermanently(job, request, "POLICY_VIOLATION",
                        "The request no longer satisfies the resource policy: " + e.getMessage());
                return Optional.empty();
            }
            job.countStartAttempt();
        }
        ProvisioningCommand command = new ProvisioningCommand(request.getId(), request.getRequestNumber(),
                request.getResourceType(), request.getEnvironment(), requests.spec(request), job.getIdempotencyKey());
        return Optional.of(new Execution(jobId, command, kind, job.getProviderOperationId(), job.getAttemptCount()));
    }

    @Transactional
    public void record(Execution execution, String workerId, Instant startedAt, ProvisioningOutcome outcome) {
        ProvisioningJob job = jobs.findForUpdate(execution.jobId()).orElseThrow();
        if (!job.isLeasedBy(workerId)) {
            // Our lease expired and another worker took over; its result wins. Idempotent adapters make
            // the duplicate call harmless.
            log.warn("Lease lost for provisioning job {}; discarding outcome {}", job.getId(), outcome.getClass().getSimpleName());
            return;
        }
        ResourceRequest request = requests.lockForUpdate(job.getRequestId());
        Instant now = Instant.now(clock);
        attempts.save(toAttempt(execution, workerId, startedAt, now, outcome));

        switch (outcome) {
            case Completed completed -> succeed(job, request, completed, now);
            case InProgress inProgress -> awaitProvider(job, request, inProgress, now);
            case Failed failed when failed.retryable() -> retryOrFail(job, request, failed, now);
            case Failed failed -> failPermanently(job, request, failed.code(), failed.message());
        }
    }

    private void succeed(ProvisioningJob job, ResourceRequest request, Completed completed, Instant now) {
        job.succeed();
        results.save(new ProvisioningResult(job.getId(), request.getId(), completed.externalResourceId(),
                jsonMapper.writeValueAsString(completed.details()), now));
        request.markProvisioned();
        audit.record(Actor.system(), AuditAction.PROVISIONING_SUCCEEDED, AuditEntityType.RESOURCE_REQUEST,
                request.getId(), Map.of("externalResourceId", completed.externalResourceId()));
        events.publishEvent(new ProvisioningFinished(request.getId(), request.getRequestNumber(),
                request.getRequesterId(), true, completed.externalResourceId()));
    }

    private void awaitProvider(ProvisioningJob job, ResourceRequest request, InProgress inProgress, Instant now) {
        Instant operationStart = job.getOperationStartedAt() != null ? job.getOperationStartedAt() : now;
        if (now.isAfter(operationStart.plus(properties.operationTimeout()))) {
            failPermanently(job, request, "OPERATION_TIMEOUT",
                    "The provider did not finish within " + properties.operationTimeout() + ".");
            return;
        }
        job.awaitProvider(inProgress.operationId(), now.plus(clamp(inProgress.pollAfter())), now);
    }

    private void retryOrFail(ProvisioningJob job, ResourceRequest request, Failed failed, Instant now) {
        if (!retryPolicy.canRetry(job.getAttemptCount())) {
            failPermanently(job, request, failed.code(),
                    failed.message() + " (gave up after " + job.getAttemptCount() + " attempts)");
            return;
        }
        job.scheduleRetry(now.plus(retryPolicy.delayAfter(job.getAttemptCount())), failed.code(), failed.message());
        audit.record(Actor.system(), AuditAction.PROVISIONING_ATTEMPT_FAILED, AuditEntityType.RESOURCE_REQUEST,
                request.getId(), Map.of("errorCode", failed.code(), "attempt", job.getAttemptCount(), "retryAt",
                        job.getNextRunAt()));
    }

    /** Final failure. The approval history stays untouched; only request and job status change. */
    private void failPermanently(ProvisioningJob job, ResourceRequest request, String code, String message) {
        job.fail(code, message);
        if (request.getStatus() == RequestStatus.PROVISIONING) {
            request.markProvisioningFailed();
        }
        audit.record(Actor.system(), AuditAction.PROVISIONING_FAILED, AuditEntityType.RESOURCE_REQUEST,
                request.getId(), Map.of("errorCode", code, "message", job.getLastErrorMessage()));
        events.publishEvent(new ProvisioningFinished(request.getId(), request.getRequestNumber(),
                request.getRequesterId(), false, job.getLastErrorMessage()));
    }

    private static ProvisioningAttempt toAttempt(Execution execution, String workerId, Instant startedAt,
            Instant finishedAt, ProvisioningOutcome outcome) {
        ProvisioningAttempt.Outcome result;
        String code = null;
        String message = null;
        String operationId = execution.operationId();
        switch (outcome) {
            case Completed completed -> result = ProvisioningAttempt.Outcome.SUCCEEDED;
            case InProgress inProgress -> {
                result = ProvisioningAttempt.Outcome.IN_PROGRESS;
                operationId = inProgress.operationId();
            }
            case Failed failed -> {
                result = failed.retryable() ? ProvisioningAttempt.Outcome.RETRYABLE_FAILURE
                        : ProvisioningAttempt.Outcome.PERMANENT_FAILURE;
                code = failed.code();
                message = failed.message();
            }
        }
        return new ProvisioningAttempt(execution.jobId(), execution.attemptNumber(), execution.kind(), workerId,
                startedAt, finishedAt, result, code, message, operationId);
    }

    private static Duration clamp(Duration pollAfter) {
        if (pollAfter.compareTo(MIN_POLL) < 0) {
            return MIN_POLL;
        }
        return pollAfter.compareTo(MAX_POLL) > 0 ? MAX_POLL : pollAfter;
    }
}
