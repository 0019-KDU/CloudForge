package com.cloudforge.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;
import com.cloudforge.identity.repository.ManagerHierarchyLock;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.organization.application.OrganizationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock
    UserRepository users;
    @Mock
    ManagerHierarchyLock hierarchyLock;
    @Mock
    OrganizationService organization;
    @Mock
    RefreshTokenService refreshTokens;
    @Mock
    AuditLog audit;

    final UUID adminId = UUID.randomUUID();

    UserAdministrationService service;

    User engineer;
    User manager;

    @BeforeEach
    void setUp() {
        service = new UserAdministrationService(users, hierarchyLock, organization, refreshTokens, audit);
        engineer = user("eng@example.com", Role.ENGINEER);
        manager = user("mgr@example.com", Role.MANAGER);
    }

    @Test
    void assignsEligibleManagerUnderTheHierarchyLock() {
        when(users.isInManagementChain(manager.getId(), engineer.getId())).thenReturn(false);

        User result = service.assignManager(adminId, engineer.getId(), manager.getId(), null);

        assertThat(result.getManagerId()).isEqualTo(manager.getId());
        verify(hierarchyLock).acquire();
    }

    @Test
    void rejectsManagerWithoutManagerRole() {
        User otherEngineer = user("eng2@example.com", Role.ENGINEER);

        assertThatThrownBy(() -> service.assignManager(adminId, engineer.getId(), otherEngineer.getId(), null))
                .hasFieldOrPropertyWithValue("code", "MANAGER_NOT_ELIGIBLE");
        assertThat(engineer.getManagerId()).isNull();
    }

    @Test
    void rejectsDisabledManager() {
        manager.disable();

        assertThatThrownBy(() -> service.assignManager(adminId, engineer.getId(), manager.getId(), null))
                .hasFieldOrPropertyWithValue("code", "MANAGER_NOT_ELIGIBLE");
    }

    @Test
    void rejectsAssignmentThatWouldCreateCycle() {
        when(users.isInManagementChain(manager.getId(), engineer.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.assignManager(adminId, engineer.getId(), manager.getId(), null))
                .hasFieldOrPropertyWithValue("code", "MANAGER_CYCLE");
        assertThat(engineer.getManagerId()).isNull();
    }

    @Test
    void rejectsSelfAssignmentWithoutQueryingTheHierarchy() {
        assertThatThrownBy(() -> service.assignManager(adminId, manager.getId(), manager.getId(), null))
                .hasFieldOrPropertyWithValue("code", "MANAGER_SELF_ASSIGNMENT");
        verify(users, never()).isInManagementChain(any(), any());
    }

    @Test
    void clearingManagerNeedsNoEligibilityCheck() {
        service.assignManager(adminId, engineer.getId(), null, null);

        assertThat(engineer.getManagerId()).isNull();
        verify(users, never()).isInManagementChain(any(), any());
    }

    @Test
    void cannotDisableManagerWhoStillHasActiveReports() {
        when(users.existsByManagerIdAndStatus(manager.getId(), UserStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.disable(adminId, manager.getId(), null))
                .hasFieldOrPropertyWithValue("code", "MANAGER_HAS_REPORTS");
        assertThat(manager.isActive()).isTrue();
    }

    @Test
    void cannotRemoveManagerRoleWhileUserHasActiveReports() {
        when(users.existsByManagerIdAndStatus(manager.getId(), UserStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.replaceRoles(adminId, manager.getId(), Set.of(Role.ENGINEER), null))
                .hasFieldOrPropertyWithValue("code", "MANAGER_HAS_REPORTS");
        assertThat(manager.hasRole(Role.MANAGER)).isTrue();
    }

    @Test
    void assigningUnknownTeamFails() {
        UUID teamId = UUID.randomUUID();
        when(organization.requireTeam(teamId)).thenThrow(new NotFoundException("TEAM_NOT_FOUND", "Team was not found."));

        assertThatThrownBy(() -> service.assignTeam(adminId, engineer.getId(), teamId, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(engineer.getTeamId()).isNull();
    }

    @Test
    void adminCannotDisableThemselves() {
        assertThatThrownBy(() -> service.disable(adminId, adminId, null))
                .hasFieldOrPropertyWithValue("code", "SELF_LOCKOUT");
        verify(hierarchyLock, never()).acquire();
    }

    @Test
    void adminCannotRemoveTheirOwnAdminRole() {
        assertThatThrownBy(() -> service.replaceRoles(adminId, adminId, Set.of(Role.MANAGER), null))
                .hasFieldOrPropertyWithValue("code", "SELF_LOCKOUT");
    }

    @Test
    void disablingRevokesRefreshTokens() {
        service.disable(adminId, engineer.getId(), null);

        assertThat(engineer.isActive()).isFalse();
        verify(refreshTokens).revokeAllForUser(engineer.getId());
    }

    @Test
    void roleChangeRevokesRefreshTokensSoNewRolesApplyQuickly() {
        service.replaceRoles(adminId, engineer.getId(), Set.of(Role.ENGINEER, Role.AUDITOR), null);

        verify(refreshTokens).revokeAllForUser(engineer.getId());
    }

    private User user(String email, Role role) {
        User user = new User(email, email, "hash", Set.of(role));
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", id);
        lenient().when(users.findById(id)).thenReturn(Optional.of(user));
        return user;
    }
}
