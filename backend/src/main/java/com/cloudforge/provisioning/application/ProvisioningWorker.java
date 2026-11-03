package com.cloudforge.provisioning.application;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.common.logging.Redaction;
import com.cloudforge.common.web.CorrelationIdFilter;
import com.cloudforge.provisioning.ProvisioningProperties;
import com.cloudforge.provisioning.application.ProvisioningJobLifecycle.Execution;
import com.cloudforge.provisioning.domain.ProvisioningAttempt;
import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.provisioning.provider.ProviderResolver;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Executes due provisioning jobs: claim → begin → provider call (no transaction) → record.
 * Safe to run concurrently on any number of instances; see {@link ProvisioningJobClaimer}.
 *
 * <p>Scheduling is separate ({@code ProvisioningScheduler}) so tests and operators can drive
 * {@link #runOnce()} directly.
 */
@Component
public class ProvisioningWorker {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningWorker.class);

    private final ProvisioningJobClaimer claimer;
    private final ProvisioningJobLifecycle lifecycle;
    private final ProviderResolver providers;
    private final ProvisioningProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;
    private final String workerId;

    ProvisioningWorker(ProvisioningJobClaimer claimer, ProvisioningJobLifecycle lifecycle, ProviderResolver providers,
            ProvisioningProperties properties, MeterRegistry meters, Clock clock) {
        this.claimer = claimer;
        this.lifecycle = lifecycle;
        this.providers = providers;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
        // pid@host plus a random suffix: unique per process, readable in the job table.
        this.workerId = ManagementFactory.getRuntimeMXBean().getName() + "#" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Claims and processes one batch of due jobs; returns how many were claimed. */
    public int runOnce() {
        List<UUID> claimed = claimer.claim(workerId, properties.worker().batchSize(), properties.worker().lease());
        for (UUID jobId : claimed) {
            process(jobId);
        }
        return claimed.size();
    }

    private void process(UUID jobId) {
        MDC.put(CorrelationIdFilter.MDC_KEY, "job-" + jobId);
        try {
            Optional<Execution> execution = lifecycle.begin(jobId, workerId);
            if (execution.isEmpty()) {
                return;
            }
            Instant startedAt = Instant.now(clock);
            ProvisioningOutcome outcome = execute(execution.get());
            lifecycle.record(execution.get(), workerId, startedAt, outcome);
            meters.counter("cloudforge.provisioning.executions",
                    "type", execution.get().command().type().name(),
                    "kind", execution.get().kind().name(),
                    "outcome", outcome.getClass().getSimpleName()).increment();
        } catch (RuntimeException e) {
            // A database or programming error: the job keeps its lease and is recovered when it expires.
            log.error("Provisioning job {} could not be processed; it will be retried after the lease expires", jobId,
                    Redaction.redact(e));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private ProvisioningOutcome execute(Execution execution) {
        Optional<CloudProvisioningProvider> provider = providers.resolve(execution.command().type());
        if (provider.isEmpty()) {
            return ProvisioningOutcome.Failed.permanent("PROVIDER_DISABLED",
                    "Provisioning for " + execution.command().type() + " is not enabled on this platform.");
        }
        try {
            return execution.kind() == ProvisioningAttempt.Kind.START
                    ? provider.get().start(execution.command())
                    : provider.get().poll(execution.command(), execution.operationId());
        } catch (RuntimeException e) {
            // Adapters should not throw; if one does, treat it as transient and keep details in the log only.
            log.error("Provider {} threw unexpectedly for job {}", provider.get().getClass().getSimpleName(),
                    execution.jobId(), Redaction.redact(e));
            return ProvisioningOutcome.Failed.retryable("PROVIDER_ERROR", "Unexpected error while calling the provider.");
        }
    }

    String workerId() {
        return workerId;
    }
}
