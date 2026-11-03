package com.cloudforge.identity.domain;

import java.util.Locale;
import java.util.Objects;

/** The single place where email addresses are normalized before storage or lookup. */
public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        return Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
    }
}
