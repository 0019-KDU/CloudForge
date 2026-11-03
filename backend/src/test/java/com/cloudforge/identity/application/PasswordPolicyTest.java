package com.cloudforge.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongUncommonPassphrase() {
        assertThatCode(() -> policy.validate("purple tractor on the moon", "alice@example.com"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "elevenchars", "PASSWORD1234", "Qwerty123456"})
    void rejectsShortOrCommonPasswords(String password) {
        assertThatThrownBy(() -> policy.validate(password, "alice@example.com"))
                .hasFieldOrPropertyWithValue("code", "WEAK_PASSWORD");
    }

    @Test
    void rejectsPasswordContainingEmailName() {
        assertThatThrownBy(() -> policy.validate("my name is alice.smith!", "alice.smith@example.com"))
                .hasFieldOrPropertyWithValue("code", "WEAK_PASSWORD");
    }

    @Test
    void rejectsPasswordsBeyondBcryptLimitMeasuredInBytes() {
        String twentyFiveEmoji = "😀".repeat(25); // 25 characters but 100 UTF-8 bytes

        assertThatThrownBy(() -> policy.validate(twentyFiveEmoji, "alice@example.com"))
                .hasMessageContaining("72 bytes");
    }

    @Test
    void countsCodePointsNotUtf16UnitsForMinimumLength() {
        String twelveEmoji = "😀".repeat(12); // 12 code points, 48 bytes

        assertThatCode(() -> policy.validate(twelveEmoji, "alice@example.com")).doesNotThrowAnyException();
    }
}
