package com.cloudforge.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.common.security.CurrentUser;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class RequestAccessPolicyTest {

    private final RequestAccessPolicy policy = new RequestAccessPolicy();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID approverId = UUID.randomUUID();
    private final RequestParticipants submitted = new RequestParticipants(requesterId, approverId);

    private static CurrentUser user(UUID id, String... roles) {
        return new CurrentUser(id, Arrays.stream(roles).map(r -> "ROLE_" + r).collect(Collectors.toSet()));
    }

    @Test
    void requesterApproverAdminAndAuditorCanView() {
        assertThat(policy.canView(user(requesterId, "ENGINEER"), submitted)).isTrue();
        assertThat(policy.canView(user(approverId, "MANAGER"), submitted)).isTrue();
        assertThat(policy.canView(user(UUID.randomUUID(), "PLATFORM_ADMIN"), submitted)).isTrue();
        assertThat(policy.canView(user(UUID.randomUUID(), "AUDITOR"), submitted)).isTrue();
    }

    @Test
    void otherEngineersAndOtherManagersSeeNotFound() {
        assertThatThrownBy(() -> policy.requireView(user(UUID.randomUUID(), "ENGINEER"), submitted))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> policy.requireView(user(UUID.randomUUID(), "MANAGER"), submitted))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyTheRequesterMayChangeTheirRequest() {
        assertThatCode(() -> policy.requireOwner(user(requesterId, "ENGINEER"), submitted)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireOwner(user(approverId, "MANAGER"), submitted))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireOwner(user(UUID.randomUUID(), "PLATFORM_ADMIN"), submitted))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyTheAssignedManagerDecides() {
        assertThatCode(() -> policy.requireDecider(user(approverId, "MANAGER"), submitted)).doesNotThrowAnyException();
        // An administrator can see the request but is not the approver.
        assertThatThrownBy(() -> policy.requireDecider(user(UUID.randomUUID(), "PLATFORM_ADMIN", "MANAGER"), submitted))
                .isInstanceOf(AccessDeniedException.class);
        // A manager who is not assigned cannot even see it.
        assertThatThrownBy(() -> policy.requireDecider(user(UUID.randomUUID(), "MANAGER"), submitted))
                .isInstanceOf(NotFoundException.class);
        // The assigned approver whose MANAGER role was removed in the meantime.
        assertThatThrownBy(() -> policy.requireDecider(user(approverId, "ENGINEER"), submitted))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void selfApprovalIsRejectedEvenWithInconsistentData() {
        RequestParticipants inconsistent = new RequestParticipants(requesterId, requesterId);

        assertThatThrownBy(() -> policy.requireDecider(user(requesterId, "MANAGER"), inconsistent))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void listScopesAreRoleRestricted() {
        assertThatCode(() -> policy.requireListScope(user(requesterId, "ENGINEER"), RequestListScope.MINE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireListScope(user(requesterId, "ENGINEER"), RequestListScope.ALL))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireListScope(user(requesterId, "ENGINEER"), RequestListScope.ASSIGNED))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.requireListScope(user(requesterId, "AUDITOR"), RequestListScope.ALL)).doesNotThrowAnyException();
    }

    @Test
    void onlyAdminsAndManagersOrEngineersHaveTheirOwnPowers() {
        assertThatThrownBy(() -> policy.requireCanCreate(user(UUID.randomUUID(), "AUDITOR")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireProvisioningOperator(user(requesterId, "ENGINEER"), submitted))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.requireProvisioningOperator(user(UUID.randomUUID(), "PLATFORM_ADMIN"), submitted))
                .doesNotThrowAnyException();
    }
}
