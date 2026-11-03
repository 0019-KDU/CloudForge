package com.cloudforge.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * A random bearer secret (invitation or refresh token). The raw value is handed out exactly once;
 * the database keeps only its SHA-256 hash. A fast hash is fine here, unlike for passwords: the token
 * has 256 bits of entropy, so it cannot be brute-forced from a leaked hash.
 */
public record OpaqueToken(String value) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    public static OpaqueToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return new OpaqueToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    @Override
    public String toString() {
        return "OpaqueToken[redacted]";
    }
}
