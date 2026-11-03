package com.cloudforge.identity.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.InvalidRequestException;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.organization.application.OrganizationService;
import com.cloudforge.organization.domain.Department;
import com.cloudforge.organization.domain.Team;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service for the signed-in user, plus the user directory every user needs to show names
 * instead of IDs (requesters, approvers, audit actors).
 */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    static final int MAX_DIRECTORY_LOOKUP = 100;

    private final UserRepository users;
    private final UserAdministrationService userAdministration;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditLog audit;
    private final OrganizationService organization;

    public ProfileService(UserRepository users, UserAdministrationService userAdministration,
            PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
            AuditLog audit, OrganizationService organization) {
        this.users = users;
        this.userAdministration = userAdministration;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.organization = organization;
    }

    /**
     * What any colleague may know about a user: name, email, status and place in the organization. No
     * roles, manager or security state.
     */
    public record DirectoryEntry(UUID id, String displayName, String email, UserStatus status, UUID teamId,
            String teamName, UUID departmentId, String departmentName) {
    }

    /**
     * Directory data for the given IDs in three queries in total (users, their teams, those teams'
     * departments), whatever the number of IDs. Unknown IDs are simply absent from the result.
     */
    public List<DirectoryEntry> directory(Collection<UUID> ids) {
        if (ids.size() > MAX_DIRECTORY_LOOKUP) {
            throw new InvalidRequestException("TOO_MANY_IDS",
                    "At most " + MAX_DIRECTORY_LOOKUP + " users can be looked up at once.");
        }
        List<User> found = users.findAllById(ids);
        Map<UUID, Team> teams = organization.teamsById(found.stream().map(User::getTeamId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, Department> departments = organization.departmentsById(teams.values().stream()
                .map(Team::getDepartmentId).collect(Collectors.toSet()));
        return found.stream().map(user -> {
            Team team = user.getTeamId() == null ? null : teams.get(user.getTeamId());
            Department department = team == null ? null : departments.get(team.getDepartmentId());
            return new DirectoryEntry(user.getId(), user.getDisplayName(), user.getEmail(), user.getStatus(),
                    team == null ? null : team.getId(), team == null ? null : team.getName(),
                    department == null ? null : department.getId(), department == null ? null : department.getName());
        }).toList();
    }

    @Transactional
    public User updateDisplayName(UUID userId, String displayName) {
        User user = userAdministration.requireActiveUser(userId);
        user.changeDisplayName(displayName);
        audit.record(Actor.user(userId), AuditAction.PROFILE_UPDATED, AuditEntityType.USER, userId,
                Map.of("displayName", user.getDisplayName()));
        return user;
    }

    /**
     * Requires the current password, so a stolen access token alone cannot take over the account.
     * Afterwards all refresh tokens are revoked: every session, including this one, must sign in again.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userAdministration.requireActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException("INVALID_CURRENT_PASSWORD", "The current password is incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException("PASSWORD_UNCHANGED", "Choose a password different from the current one.");
        }
        passwordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId);
        audit.record(Actor.user(userId), AuditAction.PASSWORD_CHANGED, AuditEntityType.USER, userId);
    }
}
