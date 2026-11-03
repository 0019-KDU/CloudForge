package com.cloudforge.config;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token settings under {@code cloudforge.security.jwt}. Startup fails without a strong secret: there
 * is deliberately no default key.
 *
 * @param secret          base64-encoded HMAC key of at least 256 bits ({@code openssl rand -base64 32})
 * @param issuer          {@code iss} claim written and required
 * @param audience        {@code aud} claim written and required
 * @param accessTokenTtl  lifetime of access tokens, which is also the worst-case delay before a
 *                        disabled user or a role change takes effect
 * @param refreshTokenTtl lifetime of a refresh token family's tokens
 */
@ConfigurationProperties("cloudforge.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("cloudforge") String issuer,
        @DefaultValue("cloudforge-api") String audience,
        @DefaultValue("PT10M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "cloudforge.security.jwt.secret is not set (env CLOUDFORGE_JWT_SECRET). Generate one with: openssl rand -base64 32");
        }
        if (secretBytes(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("cloudforge.security.jwt.secret must decode to at least 256 bits");
        }
    }

    public byte[] secretBytes() {
        return secretBytes(secret);
    }

    private static byte[] secretBytes(String secret) {
        try {
            return Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("cloudforge.security.jwt.secret must be base64-encoded", e);
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=<redacted>, issuer=" + issuer + ", audience=" + audience
                + ", accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
