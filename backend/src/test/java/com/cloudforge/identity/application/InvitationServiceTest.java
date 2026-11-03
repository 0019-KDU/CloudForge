package com.cloudforge.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.identity.domain.Invitation;
import com.cloudforge.identity.domain.InvitationStatus;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.repository.InvitationRepository;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.organization.application.OrganizationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    @Mock
    InvitationRepository invitations;
    @Mock
    UserRepository users;
    @Mock
    UserAdministrationService userAdministration;
    @Mock
    OrganizationService organization;
    @Mock
    AuditLog audit;

    InvitationService service;

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitations, users, userAdministration, organization,
                new IdentityProperties(Duration.ofDays(7)), audit, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(invitations.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void issuesInvitationStoringOnlyTheTokenHash() {
        var issued = service.invite(ADMIN, "New@Example.com", Set.of(Role.ENGINEER), null, null);

        Invitation invitation = issued.invitation();
        assertThat(invitation.getEmail()).isEqualTo("new@example.com");
        assertThat(invitation.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(issued.token().value()).isNotBlank();
        assertThat(invitation.getInvitedBy()).isEqualTo(ADMIN);
        assertThat(invitation).extracting("tokenHash")
                .isEqualTo(issued.token().hash())
                .isNotEqualTo(issued.token().value());
    }

    @Test
    void rejectsEmailThatAlreadyBelongsToAUser() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.invite(ADMIN, "Taken@example.com", Set.of(Role.ENGINEER), null, null))
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_REGISTERED");
        verify(invitations, never()).save(any());
    }

    @Test
    void rejectsSecondOpenInvitationForSameEmail() {
        Invitation open = new Invitation("dup@example.com", "h", Set.of(Role.ENGINEER), null, null, NOW.plusSeconds(60), null);
        when(invitations.findByEmailAndStatus("dup@example.com", InvitationStatus.PENDING)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.invite(ADMIN, "dup@example.com", Set.of(Role.ENGINEER), null, null))
                .hasFieldOrPropertyWithValue("code", "INVITATION_ALREADY_PENDING");
    }

    @Test
    void replacesExpiredPendingInvitation() {
        Invitation stale = new Invitation("again@example.com", "h", Set.of(Role.ENGINEER), null, null, NOW.minusSeconds(1), null);
        when(invitations.findByEmailAndStatus("again@example.com", InvitationStatus.PENDING)).thenReturn(Optional.of(stale));

        var issued = service.invite(ADMIN, "again@example.com", Set.of(Role.ENGINEER), null, null);

        assertThat(stale.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        verify(invitations).saveAndFlush(stale);
        assertThat(issued.invitation().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void validatesManagerAndTeam() {
        UUID managerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        service.invite(ADMIN, "x@example.com", Set.of(Role.ENGINEER), managerId, teamId);

        verify(userAdministration).requireEligibleManager(managerId);
        verify(organization).requireTeam(teamId);
    }
}
