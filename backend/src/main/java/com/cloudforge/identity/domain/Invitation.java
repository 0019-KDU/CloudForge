package com.cloudforge.identity.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.persistence.BaseEntity;

/**
 * An administrator's permission for one email address to register. This is the onboarding policy:
 * there is no open sign-up. The roles, manager and team are fixed by the administrator, so a
 * registrant can never choose their own privileges. Only the SHA-256 hash of the token is stored.
 */
@Entity
@Table(name = "invitation")
public class Invitation extends BaseEntity {

    @Column(nullable = false, length = 254, updatable = false)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "manager_id", updatable = false)
    private UUID managerId;

    @Column(name = "team_id", updatable = false)
    private UUID teamId;

    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @ElementCollection(fetch = FetchType.EAGER) // tiny set, always needed; batch-fetched for pages
    @CollectionTable(name = "invitation_role", joinColumns = @JoinColumn(name = "invitation_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected Invitation() {
        // for JPA
    }

    public Invitation(String email, String tokenHash, Set<Role> roles, UUID managerId, UUID teamId, Instant expiresAt,
            UUID invitedBy) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessRuleViolationException("ROLES_REQUIRED", "An invitation must grant at least one role.");
        }
        this.email = EmailAddress.normalize(email);
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.roles.addAll(roles);
        this.managerId = managerId;
        this.teamId = teamId;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.invitedBy = invitedBy;
        this.status = InvitationStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Pending and not yet expired: the only state in which the token can be redeemed. */
    public boolean isRedeemable(Instant now) {
        return status == InvitationStatus.PENDING && !isExpired(now);
    }

    public void revoke() {
        if (status != InvitationStatus.PENDING) {
            throw new BusinessRuleViolationException("INVITATION_NOT_PENDING",
                    "Only a pending invitation can be revoked.");
        }
        status = InvitationStatus.REVOKED;
    }

    public void markAccepted(Instant now) {
        if (!isRedeemable(now)) {
            throw new BusinessRuleViolationException("INVITATION_NOT_REDEEMABLE",
                    "The invitation is invalid or has expired.");
        }
        status = InvitationStatus.ACCEPTED;
    }

    public String getEmail() {
        return email;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
