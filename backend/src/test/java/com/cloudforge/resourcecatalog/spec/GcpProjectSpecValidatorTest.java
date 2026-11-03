package com.cloudforge.resourcecatalog.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cloudforge.common.error.FieldViolation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GcpProjectSpecValidatorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final GcpProjectSpecValidator validator = new GcpProjectSpecValidator();
    private final JsonNode constraints = json.readTree("{\"allowedFolderIds\": [\"123456789012\"]}");

    private List<String> fieldsOf(String spec) {
        return validator.validateSpec(json.readTree(spec), constraints).stream().map(FieldViolation::field).toList();
    }

    @Test
    void acceptsValidSpec() {
        assertThat(fieldsOf("{\"projectId\": \"acme-payments-dev\", \"displayName\": \"Payments dev\", "
                + "\"folderId\": \"123456789012\"}")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "1starts-with-digit", "ends-with-hyphen-", "Upper-case-id",
            "has_underscore1", "this-project-id-is-far-too-long-x", "my-google-project"})
    void rejectsInvalidProjectIds(String projectId) {
        assertThat(fieldsOf("{\"projectId\": \"" + projectId + "\", \"folderId\": \"123456789012\"}"))
                .containsExactly("spec.projectId");
    }

    @Test
    void acceptsBoundaryLengths() {
        assertThat(fieldsOf("{\"projectId\": \"abcdef\", \"folderId\": \"123456789012\"}")).isEmpty();
        assertThat(fieldsOf("{\"projectId\": \"a" + "b".repeat(29) + "\", \"folderId\": \"123456789012\"}")).isEmpty();
    }

    @Test
    void folderMustBeApprovedByPolicy() {
        assertThat(fieldsOf("{\"projectId\": \"acme-dev\", \"folderId\": \"999\"}")).containsExactly("spec.folderId");
    }

    @Test
    void validatesDisplayNameCharacters() {
        assertThat(fieldsOf("{\"projectId\": \"acme-dev\", \"folderId\": \"123456789012\", \"displayName\": \"<b>x</b>\"}"))
                .containsExactly("spec.displayName");
    }

    @Test
    void allowingPolicyMustListFolders() {
        assertThat(validator.validateConstraints(json.readTree("{\"allowedFolderIds\": []}"), true))
                .extracting(FieldViolation::field).containsExactly("constraints.allowedFolderIds");
        assertThat(validator.validateConstraints(json.readTree("{\"allowedFolderIds\": [\"folders/1\"]}"), false))
                .extracting(FieldViolation::field).containsExactly("constraints.allowedFolderIds[0]");
    }
}
