package com.cloudforge.provisioning.domain;

/**
 * {@code QUEUED} and {@code AWAITING_PROVIDER} jobs are due at {@code next_run_at}; {@code RUNNING}
 * means a worker holds the lease. {@code SUCCEEDED} and {@code FAILED} are final until an
 * administrator retries a failed job.
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    AWAITING_PROVIDER,
    SUCCEEDED,
    FAILED
}
