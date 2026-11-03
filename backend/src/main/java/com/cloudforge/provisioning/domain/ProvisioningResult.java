package com.cloudforge.provisioning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** The provisioned resource: its provider identifier and non-secret details. */
@Entity
@Immutable
@Table(name = "provisioning_result")
public class ProvisioningResult {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "external_resource_id", nullable = false, length = 255)
    private String externalResourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProvisioningResult() {
        // for JPA
    }

    public ProvisioningResult(UUID jobId, UUID requestId, String externalResourceId, String detailsJson, Instant createdAt) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.externalResourceId = Objects.requireNonNull(externalResourceId, "externalResourceId");
        this.detailsJson = Objects.requireNonNull(detailsJson, "detailsJson");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getExternalResourceId() {
        return externalResourceId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
