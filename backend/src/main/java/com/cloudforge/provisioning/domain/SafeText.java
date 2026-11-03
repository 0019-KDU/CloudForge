package com.cloudforge.provisioning.domain;

/**
 * Last line of defence for provider error text before it is stored or shown: control characters are
 * removed (no log or terminal injection) and length is capped. Adapters are still responsible for
 * never passing credentials or raw payloads.
 */
final class SafeText {

    private SafeText() {
    }

    static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String clean = text.replaceAll("\\p{Cntrl}", " ").strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1) + "…";
    }
}
