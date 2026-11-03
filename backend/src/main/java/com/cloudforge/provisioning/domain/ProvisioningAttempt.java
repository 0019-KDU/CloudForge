package com.cloudforge.provisioning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/** One execution of a job against a provider (a start or a poll). Insert-only history. */
@Entity
@Immutable
@Table(name = "provisioning_attempt")
public class ProvisioningAttempt {

    public enum Kind {
        START,
        POLL
    }

    public enum Outcome {
        SUCCEEDED,
        IN_PROGRESS,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "worker_id", nullable = false, length = 100)
    private String workerId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "provider_operation_id", length = 500)
    private String providerOperationId;

    protected ProvisioningAttempt() {
        // for JPA
    }

    public ProvisioningAttempt(UUID jobId, int attemptNumber, Kind kind, String workerId, Instant startedAt,
            Instant finishedAt, Outcome outcome, String errorCode, String errorMessage, String providerOperationId) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.attemptNumber = attemptNumber;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.errorCode = errorCode;
        this.errorMessage = SafeText.truncate(errorMessage, 1000);
        this.providerOperationId = providerOperationId;
    }

    public UUID getId() {
        return id;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getProviderOperationId() {
        return providerOperationId;
    }
}
