package com.cloudforge.identity.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.cloudforge.common.error.BusinessRuleViolationException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Password rules following NIST SP 800-63B: length over complexity, a blocklist of common passwords,
 * and no composition rules or forced rotation.
 *
 * <p>The 72-byte maximum is a bcrypt limit: bytes beyond it are ignored when hashing, so a longer
 * password would give a false sense of strength (Spring Security rejects such passwords outright).
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_BYTES = 72;

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this.commonPasswords = loadCommonPasswords();
    }

    public void validate(String password, String email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw weak("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw weak("Password must be at most " + MAX_BYTES + " bytes long.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            throw weak("This password is too common. Choose a less predictable one.");
        }
        String localPart = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            throw weak("Password must not contain your email name.");
        }
    }

    private static BusinessRuleViolationException weak(String message) {
        return new BusinessRuleViolationException("WEAK_PASSWORD", message);
    }

    private static Set<String> loadCommonPasswords() {
        var resource = new ClassPathResource("identity/common-passwords.txt");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load common password list", e);
        }
    }
}
