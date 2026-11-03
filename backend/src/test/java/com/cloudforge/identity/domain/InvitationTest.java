package com.cloudforge.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import com.cloudforge.common.error.BusinessRuleViolationException;

import org.junit.jupiter.api.Test;

class InvitationTest {

    private static final Instant EXPIRES = Instant.parse("2026-11-06T00:00:00Z");

    private Invitation invitation() {
        return new Invitation("New.Hire@Example.com", "hash", Set.of(Role.ENGINEER), null, null, EXPIRES, null);
    }

    @Test
    void newInvitationIsPendingWithNormalizedEmail() {
        Invitation invitation = invitation();

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getEmail()).isEqualTo("new.hire@example.com");
    }

    @Test
    void expiresExactlyAtExpiryInstant() {
        Invitation invitation = invitation();

        assertThat(invitation.isRedeemable(EXPIRES.minusSeconds(1))).isTrue();
        assertThat(invitation.isRedeemable(EXPIRES)).isFalse();
    }

    @Test
    void cannotAcceptExpiredInvitation() {
        Invitation invitation = invitation();

        assertThatThrownBy(() -> invitation.markAccepted(EXPIRES.plusSeconds(1)))
                .hasFieldOrPropertyWithValue("code", "INVITATION_NOT_REDEEMABLE");
    }

    @Test
    void revokedInvitationCannotBeAcceptedOrRevokedAgain() {
        Invitation invitation = invitation();
        invitation.revoke();

        assertThat(invitation.isRedeemable(EXPIRES.minusSeconds(60))).isFalse();
        assertThatThrownBy(invitation::revoke)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVITATION_NOT_PENDING");
    }

    @Test
    void requiresAtLeastOneRole() {
        assertThatThrownBy(() -> new Invitation("a@example.com", "hash", Set.of(), null, null, EXPIRES, null))
                .hasFieldOrPropertyWithValue("code", "ROLES_REQUIRED");
    }
}
