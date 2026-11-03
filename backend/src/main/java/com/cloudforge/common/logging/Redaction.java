package com.cloudforge.common.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets in text and exceptions before they are logged.
 *
 * <p>Unexpected exceptions are logged in full because their stack traces are needed for diagnosis,
 * but their messages come from third-party code (cloud SDKs, JDBC drivers, HTTP clients) and may
 * echo credentials or tokens. {@link #redact(Throwable)} returns a copy of the exception chain with
 * masked messages and the original stack traces, so nothing useful for debugging is lost.
 *
 * <p>This is a safety net, not a licence: code must still never put secrets into messages.
 */
public final class Redaction {

    static final String MASK = "[REDACTED]";
    private static final int MAX_CAUSE_DEPTH = 10;

    // Order matters: scheme credentials first, so "Authorization: Bearer <token>" masks the token itself
    // rather than only the word "Bearer".
    private static final List<Pattern> SECRET_VALUES = List.of(
            // HTTP bearer and basic credentials
            Pattern.compile("(?i)(\\b(?:bearer|basic)\\s+)([A-Za-z0-9._~+/=-]{8,})"),
            // key=value / key: value / "key":"value" / key = 'value' for credential-like keys
            Pattern.compile("(?i)([\"']?(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|secret[_-]?key"
                    + "|credentials?|authorization|client[_-]?secret|private[_-]?key)[\"']?\\s*[=:]\\s*[\"']?)"
                    + "([^\\s\"',;&}]+)"));

    private static final List<Pattern> SECRET_TOKENS = List.of(
            // JSON Web Tokens
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"),
            // AWS access key IDs
            Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            // PEM private keys
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"));

    private Redaction() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Pattern pattern : SECRET_VALUES) {
            result = pattern.matcher(result).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + MASK));
        }
        for (Pattern pattern : SECRET_TOKENS) {
            result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(MASK));
        }
        return result;
    }

    /** A copy of {@code throwable} and its causes with redacted messages and the original stack traces. */
    public static Throwable redact(Throwable throwable) {
        return copy(throwable, 0);
    }

    private static Throwable copy(Throwable original, int depth) {
        if (original == null || depth >= MAX_CAUSE_DEPTH) {
            return null;
        }
        Throwable cause = original.getCause() == original ? null : copy(original.getCause(), depth + 1);
        RedactedException copy = new RedactedException(
                original.getClass().getName() + ": " + redact(String.valueOf(original.getMessage())), cause);
        copy.setStackTrace(original.getStackTrace());
        return copy;
    }

    /** Carries the original exception type in its message, since the type itself cannot be copied safely. */
    static final class RedactedException extends RuntimeException {

        RedactedException(String message, Throwable cause) {
            super(message, cause, false, true);
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }
}
