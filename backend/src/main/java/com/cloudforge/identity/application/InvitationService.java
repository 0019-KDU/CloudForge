package com.cloudforge.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.ConflictException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.identity.domain.EmailAddress;
import com.cloudforge.identity.domain.Invitation;
import com.cloudforge.identity.domain.InvitationStatus;
import com.cloudforge.identity.domain.OpaqueToken;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.repository.InvitationRepository;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.organization.application.OrganizationService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator-controlled onboarding. Redeeming an invitation (registration) is part of the
 * authentication phase.
 */
@Service
@Transactional(readOnly = true)
public class InvitationService {

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final UserAdministrationService userAdministration;
    private final OrganizationService organization;
    private final IdentityProperties properties;
    private final AuditLog audit;
    private final Clock clock;

    public InvitationService(InvitationRepository invitations, UserRepository users,
            UserAdministrationService userAdministration, OrganizationService organization,
            IdentityProperties properties, AuditLog audit, Clock clock) {
        this.invitations = invitations;
        this.users = users;
        this.userAdministration = userAdministration;
        this.organization = organization;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    /** The raw token appears only in this result; it is never stored or retrievable again. */
    public record IssuedInvitation(Invitation invitation, OpaqueToken token) {
    }

    @Transactional
    public IssuedInvitation invite(UUID actorId, String email, Set<Role> roles, UUID managerId, UUID teamId) {
        String normalizedEmail = EmailAddress.normalize(email);
        Instant now = Instant.now(clock);

        if (users.existsByEmail(normalizedEmail)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "A user with this email already exists.");
        }
        replaceExpiredPendingInvitation(normalizedEmail, now);
        if (managerId != null) {
            userAdministration.requireEligibleManager(managerId);
        }
        if (teamId != null) {
            organization.requireTeam(teamId);
        }

        OpaqueToken token = OpaqueToken.generate();
        Invitation invitation = invitations.save(new Invitation(normalizedEmail, token.hash(), roles, managerId,
                teamId, now.plus(properties.invitationTtl()), actorId));
        audit.record(Actor.user(actorId), AuditAction.INVITATION_CREATED, AuditEntityType.INVITATION,
                invitation.getId(), Map.of("email", normalizedEmail, "roles", roles));
        return new IssuedInvitation(invitation, token);
    }

    public Page<Invitation> list(InvitationStatus status, Pageable pageable) {
        return status == null ? invitations.findAll(pageable) : invitations.findByStatus(status, pageable);
    }

    public Invitation require(UUID invitationId) {
        return invitations.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("INVITATION_NOT_FOUND", "Invitation was not found."));
    }

    @Transactional
    public Invitation revoke(UUID actorId, UUID invitationId) {
        Invitation invitation = require(invitationId);
        invitation.revoke();
        audit.record(Actor.user(actorId), AuditAction.INVITATION_REVOKED, AuditEntityType.INVITATION, invitationId);
        return invitation;
    }

    private void replaceExpiredPendingInvitation(String email, Instant now) {
        invitations.findByEmailAndStatus(email, InvitationStatus.PENDING).ifPresent(pending -> {
            if (!pending.isExpired(now)) {
                throw new ConflictException("INVITATION_ALREADY_PENDING",
                        "An open invitation for this email already exists. Revoke it to issue a new one.");
            }
            pending.revoke();
            // Flush now: Hibernate runs inserts before updates, so without this the new PENDING row
            // would hit the one-pending-invitation-per-email unique index first.
            invitations.saveAndFlush(pending);
        });
    }
}
