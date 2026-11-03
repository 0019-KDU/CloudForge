package com.cloudforge.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.cloudforge.common.persistence.BaseEntity;

/** A one-time, expiring password reset issued by an administrator. Only the token hash is stored. */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetToken() {
        // for JPA
    }

    public PasswordResetToken(UUID userId, String tokenHash, UUID issuedBy, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedBy = Objects.requireNonNull(issuedBy, "issuedBy");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isRedeemable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** Consumes the token: after a reset, or when superseded by a newer one. */
    public void markUsed(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
