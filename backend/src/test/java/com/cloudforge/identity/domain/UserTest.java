package com.cloudforge.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.common.error.BusinessRuleViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    @Test
    void normalizesEmailAndStartsActive() {
        User user = new User("  Alice.Smith@Example.COM ", " Alice ", "hash", Set.of(Role.ENGINEER));

        assertThat(user.getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Alice");
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void requiresAtLeastOneRole() {
        assertThatThrownBy(() -> new User("a@example.com", "A", "hash", Set.of()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "ROLES_REQUIRED");
    }

    @Test
    void cannotBeOwnManager() {
        User user = new User("a@example.com", "A", "hash", Set.of(Role.MANAGER));
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", id);

        assertThatThrownBy(() -> user.assignManager(id))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "MANAGER_SELF_ASSIGNMENT");
    }

    @Test
    void replaceRolesReplacesTheWholeSet() {
        User user = new User("a@example.com", "A", "hash", Set.of(Role.ENGINEER, Role.AUDITOR));

        user.replaceRoles(Set.of(Role.MANAGER, Role.ENGINEER));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.MANAGER, Role.ENGINEER);
    }

    @Test
    void locksAfterFiveConsecutiveFailuresWithGrowingCappedDuration() {
        User user = new User("a@example.com", "A", "hash", Set.of(Role.ENGINEER));
        Instant now = Instant.parse("2026-10-30T10:00:00Z");

        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(now);
        }
        assertThat(user.isLocked(now)).isFalse();

        user.recordFailedLogin(now); // 5th
        assertThat(user.getLockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(1)));

        Instant afterFirstLock = now.plus(Duration.ofMinutes(2));
        user.recordFailedLogin(afterFirstLock); // 6th
        assertThat(user.getLockedUntil()).isEqualTo(afterFirstLock.plus(Duration.ofMinutes(2)));

        Instant later = afterFirstLock;
        for (int i = 0; i < 10; i++) {
            later = later.plus(Duration.ofHours(1));
            user.recordFailedLogin(later);
        }
        assertThat(user.getLockedUntil()).isEqualTo(later.plus(Duration.ofMinutes(15)));
    }

    @Test
    void failuresWhileLockedDoNotExtendTheLock() {
        User user = new User("a@example.com", "A", "hash", Set.of(Role.ENGINEER));
        Instant now = Instant.parse("2026-10-30T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(now);
        }
        Instant lockedUntil = user.getLockedUntil();

        user.recordFailedLogin(now.plusSeconds(10));

        assertThat(user.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    void successfulLoginResetsThrottling() {
        User user = new User("a@example.com", "A", "hash", Set.of(Role.ENGINEER));
        Instant now = Instant.parse("2026-10-30T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(now);
        }

        user.recordSuccessfulLogin();

        assertThat(user.isLocked(now)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void disableAndEnable() {
        User user = new User("a@example.com", "A", "hash", Set.of(Role.ENGINEER));

        user.disable();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);

        user.enable();
        assertThat(user.isActive()).isTrue();
    }
}
