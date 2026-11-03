package com.cloudforge.identity.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.cloudforge.identity.application.InvitationService.IssuedInvitation;
import com.cloudforge.identity.domain.Invitation;
import com.cloudforge.identity.domain.InvitationStatus;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;

/** Request and response bodies of the identity admin API. Password hashes never leave the module. */
final class IdentityDtos {

    private IdentityDtos() {
    }

    record UserResponse(UUID id, String email, String displayName, UserStatus status, Set<Role> roles,
            UUID managerId, UUID teamId, Instant createdAt, Instant updatedAt, Long version) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(),
                    user.getRoles(), user.getManagerId(), user.getTeamId(), user.getCreatedAt(), user.getUpdatedAt(),
                    user.getVersion());
        }
    }

    /** {@code managerId: null} removes the manager. */
    record AssignManagerRequest(UUID managerId) {
    }

    /** {@code teamId: null} removes the user from their team. */
    record AssignTeamRequest(UUID teamId) {
    }

    record ReplaceRolesRequest(@NotEmpty Set<@NotNull Role> roles) {
    }

    record CreateInvitationRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotEmpty Set<@NotNull Role> roles,
            UUID managerId,
            UUID teamId) {
    }

    record InvitationResponse(UUID id, String email, InvitationStatus status, boolean expired, Set<Role> roles,
            UUID managerId, UUID teamId, UUID invitedBy, Instant expiresAt, Instant createdAt) {

        static InvitationResponse from(Invitation invitation, Instant now) {
            return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getStatus(),
                    invitation.getStatus() == InvitationStatus.PENDING && invitation.isExpired(now),
                    invitation.getRoles(), invitation.getManagerId(), invitation.getTeamId(),
                    invitation.getInvitedBy(), invitation.getExpiresAt(), invitation.getCreatedAt());
        }
    }

    /** Returned once, at creation. The admin passes {@code token} to the invitee. */
    record IssuedInvitationResponse(InvitationResponse invitation, String token) {

        static IssuedInvitationResponse from(IssuedInvitation issued, Instant now) {
            return new IssuedInvitationResponse(InvitationResponse.from(issued.invitation(), now),
                    issued.token().value());
        }
    }
}
