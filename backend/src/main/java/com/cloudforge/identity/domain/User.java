package com.cloudforge.identity.domain;

import java.time.Duration;
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
 * A platform user. The manager and team are stored as IDs: the manager hierarchy is walked with a
 * recursive SQL query, not by navigating entity graphs, and the team belongs to another module.
 *
 * <p>Rules that need only this object are enforced here, for example that nobody manages
 * themselves. Rules that need other rows (the manager is eligible, no cycles) are checked by
 * {@code UserAdministrationService}.
 */
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    /** Consecutive failures before the account is temporarily locked. */
    static final int LOCK_THRESHOLD = 5;
    static final Duration BASE_LOCK = Duration.ofMinutes(1);
    static final Duration MAX_LOCK = Duration.ofMinutes(15);

    @Column(nullable = false, length = 254, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "team_id")
    private UUID teamId;

    @ElementCollection(fetch = FetchType.EAGER) // tiny set, always needed; batch-fetched for pages
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {
        // for JPA
    }

    public User(String email, String displayName, String passwordHash, Set<Role> roles) {
        this.email = EmailAddress.normalize(email);
        this.displayName = Objects.requireNonNull(displayName, "displayName").strip();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = UserStatus.ACTIVE;
        replaceRoles(roles);
    }

    public void assignManager(UUID newManagerId) {
        if (newManagerId != null && newManagerId.equals(getId())) {
            throw new BusinessRuleViolationException("MANAGER_SELF_ASSIGNMENT", "A user cannot be their own manager.");
        }
        this.managerId = newManagerId;
    }

    public void assignTeam(UUID newTeamId) {
        this.teamId = newTeamId;
    }

    public void replaceRoles(Set<Role> newRoles) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new BusinessRuleViolationException("ROLES_REQUIRED", "A user must have at least one role.");
        }
        // Mutate in place: replacing the collection instance would make Hibernate delete and re-insert all rows.
        roles.retainAll(newRoles);
        roles.addAll(newRoles);
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void enable() {
        this.status = UserStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Counts a failed password check. From the {@value #LOCK_THRESHOLD}th consecutive failure the
     * account is locked for 1, 2, 4, 8, then at most 15 minutes. Attempts made while locked are not
     * counted, so the lock does not grow without bound.
     */
    public void recordFailedLogin(Instant now) {
        if (isLocked(now)) {
            return;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= LOCK_THRESHOLD) {
            int doublings = Math.min(failedLoginAttempts - LOCK_THRESHOLD, 4);
            Duration lock = BASE_LOCK.multipliedBy(1L << doublings);
            lockedUntil = now.plus(lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock);
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void changeDisplayName(String newDisplayName) {
        this.displayName = Objects.requireNonNull(newDisplayName, "displayName").strip();
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
