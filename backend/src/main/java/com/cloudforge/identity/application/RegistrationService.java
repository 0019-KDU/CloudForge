package com.cloudforge.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.CloudForgeException;
import com.cloudforge.common.error.ConflictException;
import com.cloudforge.identity.domain.Invitation;
import com.cloudforge.identity.domain.OpaqueToken;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.repository.InvitationRepository;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.organization.application.OrganizationService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redeems an invitation. Email, roles, manager and team all come from the invitation, never from the
 * registrant, so registration cannot be used to escalate privileges.
 *
 * <p>Two concurrent redemptions of one token cannot both succeed: the invitation's optimistic
 * version and the unique email constraint each stop the second one.
 */
@Service
public class RegistrationService {

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final UserAdministrationService userAdministration;
    private final OrganizationService organization;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuditLog audit;
    private final Clock clock;

    public RegistrationService(InvitationRepository invitations, UserRepository users,
            UserAdministrationService userAdministration, OrganizationService organization,
            PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, AuditLog audit, Clock clock) {
        this.invitations = invitations;
        this.users = users;
        this.userAdministration = userAdministration;
        this.organization = organization;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public User register(String invitationToken, String displayName, String password) {
        Instant now = Instant.now(clock);
        Invitation invitation = invitations.findByTokenHash(new OpaqueToken(invitationToken).hash())
                .filter(candidate -> candidate.isRedeemable(now))
                .orElseThrow(() -> new BusinessRuleViolationException("INVITATION_NOT_REDEEMABLE",
                        "The invitation is invalid or has expired."));

        if (users.existsByEmail(invitation.getEmail())) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "A user with this email already exists.");
        }
        passwordPolicy.validate(password, invitation.getEmail());
        requireInvitationStillValid(invitation);

        User user = new User(invitation.getEmail(), displayName, passwordEncoder.encode(password), invitation.getRoles());
        user.assignManager(invitation.getManagerId());
        user.assignTeam(invitation.getTeamId());
        invitation.markAccepted(now);
        User saved = users.save(user);
        audit.record(Actor.user(saved.getId()), AuditAction.USER_REGISTERED, AuditEntityType.USER, saved.getId(),
                Map.of("invitationId", invitation.getId(), "roles", saved.getRoles()));
        return saved;
    }

    /** The manager or team chosen at invitation time may have changed since; re-check before use. */
    private void requireInvitationStillValid(Invitation invitation) {
        try {
            if (invitation.getManagerId() != null) {
                userAdministration.requireEligibleManager(invitation.getManagerId());
            }
            if (invitation.getTeamId() != null) {
                organization.requireTeam(invitation.getTeamId());
            }
        } catch (CloudForgeException e) {
            throw new BusinessRuleViolationException("INVITATION_OUTDATED",
                    "This invitation is no longer valid. Ask an administrator to issue a new one.");
        }
    }
}
