package com.cloudforge.approval.domain;

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

/**
 * A manager's decision on a request. Immutable and never deleted: provisioning failures or later
 * changes do not alter the approval history. One record per request (unique constraint).
 */
@Entity
@Immutable
@Table(name = "approval_record")
public class ApprovalRecord {

    public enum Decision {
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(length = 2000)
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalRecord() {
        // for JPA
    }

    public ApprovalRecord(UUID requestId, UUID approverId, Decision decision, String comment, Instant decidedAt) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.approverId = Objects.requireNonNull(approverId, "approverId");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.comment = comment == null || comment.isBlank() ? null : comment.strip();
        if (decision == Decision.REJECTED && this.comment == null) {
            throw new IllegalArgumentException("A rejection needs a comment");
        }
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        this.createdAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
