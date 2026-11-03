package com.cloudforge.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.cloudforge.common.persistence.BaseEntity;

/**
 * One refresh token in a rotation family. A login starts a family; each refresh marks the presented
 * token as rotated and issues its successor in the same family. A rotated token that is presented
 * again means it was copied, so the caller revokes the whole family.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    protected RefreshToken() {
        // for JPA
    }

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Usable for exactly one refresh: not revoked, not already rotated, not expired. */
    public boolean isUsable(Instant now) {
        return revokedAt == null && rotatedAt == null && now.isBefore(expiresAt);
    }

    /** A rotated token presented again: the signal of a stolen token. */
    public boolean isReplayed() {
        return rotatedAt != null;
    }

    public void markRotated(Instant now) {
        rotatedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
