package com.cloudforge.provisioning.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for due jobs. Exists only when {@code cloudforge.provisioning.worker.enabled=true}, so the
 * worker can be switched off per instance (for example API-only instances) or entirely.
 */
@Component
@ConditionalOnProperty(prefix = "cloudforge.provisioning.worker", name = "enabled", havingValue = "true")
class ProvisioningScheduler {

    private final ProvisioningWorker worker;

    ProvisioningScheduler(ProvisioningWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${cloudforge.provisioning.worker.poll-interval:PT10S}")
    void poll() {
        // Drain: keep claiming while full batches come back, then wait for the next tick.
        while (worker.runOnce() > 0 && !Thread.currentThread().isInterrupted()) {
            // continue
        }
    }
}
