package com.cloudforge.identity.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;
import com.cloudforge.identity.repository.ManagerHierarchyLock;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.organization.application.OrganizationService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator use cases for existing users: manager, team, roles, enable/disable.
 *
 * <p>Invariant protected here: every active user's manager is an active user with the
 * {@code MANAGER} role, and the hierarchy has no cycles. Request submission depends on this, because
 * it captures the requester's manager as the approver.
 */
@Service
@Transactional(readOnly = true)
public class UserAdministrationService {

    private final UserRepository users;
    private final ManagerHierarchyLock hierarchyLock;
    private final OrganizationService organization;
    private final RefreshTokenService refreshTokens;
    private final AuditLog audit;

    public UserAdministrationService(UserRepository users, ManagerHierarchyLock hierarchyLock,
            OrganizationService organization, RefreshTokenService refreshTokens, AuditLog audit) {
        this.users = users;
        this.hierarchyLock = hierarchyLock;
        this.organization = organization;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    public User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User was not found."));
    }

    /**
     * The acting user must still exist and be active. Write operations call this so a disabled user's
     * not-yet-expired access token cannot change anything.
     */
    public User requireActiveUser(UUID userId) {
        return users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("The account is not active"));
    }

    public Page<User> search(UserStatus status, UUID teamId, UUID managerId, Pageable pageable) {
        return users.search(status, teamId, managerId, pageable);
    }

    @Transactional
    public User assignManager(UUID actorId, UUID userId, UUID managerId, Long expectedVersion) {
        hierarchyLock.acquire();
        User user = requireUser(userId);
        user.requireVersion(expectedVersion);
        if (managerId != null) {
            // Validate everything before touching the entity, so a rejected change leaves it unmodified.
            if (managerId.equals(userId)) {
                throw new BusinessRuleViolationException("MANAGER_SELF_ASSIGNMENT", "A user cannot be their own manager.");
            }
            requireEligibleManager(managerId);
            if (users.isInManagementChain(managerId, userId)) {
                throw new BusinessRuleViolationException("MANAGER_CYCLE",
                        "This assignment would create a cycle in the management hierarchy.");
            }
        }
        UUID previous = user.getManagerId();
        user.assignManager(managerId);
        audit.record(Actor.user(actorId), AuditAction.USER_MANAGER_CHANGED, AuditEntityType.USER, userId,
                nullable("previousManagerId", previous, "managerId", managerId));
        return user;
    }

    @Transactional
    public User assignTeam(UUID actorId, UUID userId, UUID teamId, Long expectedVersion) {
        User user = requireUser(userId);
        user.requireVersion(expectedVersion);
        if (teamId != null) {
            organization.requireTeam(teamId);
        }
        user.assignTeam(teamId);
        audit.record(Actor.user(actorId), AuditAction.USER_TEAM_CHANGED, AuditEntityType.USER, userId,
                nullable("teamId", teamId, null, null));
        return user;
    }

    /**
     * Revokes the user's refresh tokens, so the new roles apply once the current access token
     * expires (at most the access-token lifetime).
     */
    @Transactional
    public User replaceRoles(UUID actorId, UUID userId, Set<Role> roles, Long expectedVersion) {
        if (actorId.equals(userId) && !roles.contains(Role.PLATFORM_ADMIN)) {
            throw selfLockout();
        }
        hierarchyLock.acquire();
        User user = requireUser(userId);
        user.requireVersion(expectedVersion);
        if (user.hasRole(Role.MANAGER) && !roles.contains(Role.MANAGER)) {
            requireNoActiveReports(user);
        }
        Set<Role> previous = user.getRoles();
        user.replaceRoles(roles);
        refreshTokens.revokeAllForUser(userId);
        audit.record(Actor.user(actorId), AuditAction.USER_ROLES_CHANGED, AuditEntityType.USER, userId,
                Map.of("previousRoles", previous, "roles", roles));
        return user;
    }

    /** Revokes all refresh tokens: the user is locked out once their access token expires. */
    @Transactional
    public User disable(UUID actorId, UUID userId, Long expectedVersion) {
        if (actorId.equals(userId)) {
            throw selfLockout();
        }
        hierarchyLock.acquire();
        User user = requireUser(userId);
        user.requireVersion(expectedVersion);
        requireNoActiveReports(user);
        user.disable();
        refreshTokens.revokeAllForUser(userId);
        audit.record(Actor.user(actorId), AuditAction.USER_DISABLED, AuditEntityType.USER, userId);
        return user;
    }

    @Transactional
    public User enable(UUID actorId, UUID userId, Long expectedVersion) {
        User user = requireUser(userId);
        user.requireVersion(expectedVersion);
        user.enable();
        audit.record(Actor.user(actorId), AuditAction.USER_ENABLED, AuditEntityType.USER, userId);
        return user;
    }

    /** Shared with invitations: a manager must be an active user holding the MANAGER role. */
    public User requireEligibleManager(UUID managerId) {
        User manager = requireUser(managerId);
        if (!manager.isActive() || !manager.hasRole(Role.MANAGER)) {
            throw new BusinessRuleViolationException("MANAGER_NOT_ELIGIBLE",
                    "The selected manager must be an active user with the MANAGER role.");
        }
        return manager;
    }

    /** Map.of rejects nulls; audit details need to record "cleared" values. */
    private static Map<String, Object> nullable(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> details = new HashMap<>();
        details.put(k1, v1);
        if (k2 != null) {
            details.put(k2, v2);
        }
        return details;
    }

    private static BusinessRuleViolationException selfLockout() {
        return new BusinessRuleViolationException("SELF_LOCKOUT",
                "You cannot disable yourself or remove your own administrator role.");
    }

    private void requireNoActiveReports(User user) {
        if (users.existsByManagerIdAndStatus(user.getId(), UserStatus.ACTIVE)) {
            throw new BusinessRuleViolationException("MANAGER_HAS_REPORTS",
                    "Reassign this user's direct reports to another manager first.");
        }
    }
}
