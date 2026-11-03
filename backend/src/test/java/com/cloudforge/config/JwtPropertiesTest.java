package com.cloudforge.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static JwtProperties withSecret(String secret) {
        return new JwtProperties(secret, "cloudforge", "cloudforge-api", Duration.ofMinutes(10), Duration.ofDays(7));
    }

    @Test
    void missingSecretFailsFast() {
        assertThatThrownBy(() -> withSecret("")).hasMessageContaining("CLOUDFORGE_JWT_SECRET");
        assertThatThrownBy(() -> withSecret(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretShorterThan256BitsIsRejected() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> withSecret(shortSecret)).hasMessageContaining("256 bits");
    }

    @Test
    void nonBase64SecretIsRejected() {
        assertThatThrownBy(() -> withSecret("this is not base64 !!!")).hasMessageContaining("base64");
    }

    @Test
    void toStringNeverRevealsTheSecret() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(withSecret(secret).toString()).doesNotContain(secret);
    }
}
