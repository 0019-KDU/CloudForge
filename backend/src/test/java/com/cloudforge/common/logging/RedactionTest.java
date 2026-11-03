package com.cloudforge.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RedactionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "connection reset; secret=hunter2",
            "password: hunter2",
            "{\"client_secret\":\"hunter2\"}",
            "Authorization: Bearer hunter2hunter2hunter2",
            "api-key=hunter2&region=x",
            "token = 'hunter2'"})
    void masksCredentialValues(String text) {
        assertThat(Redaction.redact(text)).doesNotContain("hunter2").contains(Redaction.MASK);
    }

    @Test
    void masksKnownTokenFormats() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl";
        String text = "token rejected " + jwt + " for key AKIAIOSFODNN7EXAMPLE";

        assertThat(Redaction.redact(text)).doesNotContain(jwt).doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        String text = "Namespace 'team-a' already exists (HTTP 409) after 3 attempts";

        assertThat(Redaction.redact(text)).isEqualTo(text);
    }

    @Test
    void redactsWholeCauseChainButKeepsTypesAndStackTraces() {
        SQLException root = new SQLException("FATAL: password=hunter2 authentication failed");
        IllegalStateException top = new IllegalStateException("could not connect, token=abc123secret", root);

        Throwable redacted = Redaction.redact(top);

        StringWriter printed = new StringWriter();
        redacted.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString())
                .doesNotContain("hunter2").doesNotContain("abc123secret")
                .contains("java.lang.IllegalStateException").contains("java.sql.SQLException")
                .contains("redactsWholeCauseChainButKeepsTypesAndStackTraces");
    }
}
