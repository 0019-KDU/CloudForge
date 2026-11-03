package com.cloudforge.common.api;

import com.cloudforge.common.error.PreconditionFailedException;

/**
 * Optimistic concurrency over HTTP. Responses for versioned resources carry {@code ETag: "<version>"};
 * clients send it back as {@code If-Match} on updates, and a mismatch becomes {@code 412}. This stops
 * two administrators from silently overwriting each other's changes.
 *
 * <p>{@code If-Match} is optional, so existing clients keep working; clients that send it get
 * lost-update protection. The version is checked inside the service transaction, and the entity's
 * {@code @Version} column guards the remaining window up to commit.
 */
public final class ETags {

    private ETags() {
    }

    public static String of(Long version) {
        return "\"" + version + "\"";
    }

    /** The expected version, or {@code null} when no precondition was sent (absent or {@code *}). */
    public static Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new PreconditionFailedException("The If-Match header does not contain a valid version.");
        }
    }
}
