package com.cloudforge.resourcecatalog.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cloudforge.common.error.FieldViolation;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AwsAccountSpecValidatorTest {

    private static final String OU = "ou-ab12-cd34ef56";

    private final JsonMapper json = JsonMapper.builder().build();
    private final AwsAccountSpecValidator validator = new AwsAccountSpecValidator();
    private final JsonNode constraints = json.readTree(
            "{\"allowedOrganizationalUnitIds\": [\"" + OU + "\"], \"allowedEmailDomains\": [\"acme.com\"]}");

    private List<String> fieldsOf(String accountName, String email, String ou) {
        String spec = "{\"accountName\": \"" + accountName + "\", \"email\": \"" + email
                + "\", \"organizationalUnitId\": \"" + ou + "\"}";
        return validator.validateSpec(json.readTree(spec), constraints).stream().map(FieldViolation::field).toList();
    }

    @Test
    void acceptsValidSpec() {
        assertThat(fieldsOf("payments-dev", "aws-payments-dev@acme.com", OU)).isEmpty();
    }

    @Test
    void enforcesAccountNameLength() {
        assertThat(fieldsOf("x".repeat(50), "a1@acme.com", OU)).isEmpty();
        assertThat(fieldsOf("x".repeat(51), "a1@acme.com", OU)).containsExactly("spec.accountName");
        assertThat(fieldsOf("", "a1@acme.com", OU)).containsExactly("spec.accountName");
    }

    @Test
    void enforcesEmailFormatLengthAndApprovedDomain() {
        assertThat(fieldsOf("n", "not-an-email", OU)).containsExactly("spec.email");
        assertThat(fieldsOf("n", "someone@gmail.com", OU)).containsExactly("spec.email");
        assertThat(fieldsOf("n", "a".repeat(60) + "@acme.com", OU)).containsExactly("spec.email");
    }

    @Test
    void organizationalUnitMustBeWellFormedAndApproved() {
        assertThat(fieldsOf("n", "a1@acme.com", "r-root")).containsExactly("spec.organizationalUnitId");
        assertThat(fieldsOf("n", "a1@acme.com", "ou-zz99-zzzzzzzz")).containsExactly("spec.organizationalUnitId");
    }

    @Test
    void allowingPolicyMustListOrganizationalUnits() {
        assertThat(validator.validateConstraints(json.readTree("{}"), true))
                .extracting(FieldViolation::field).containsExactly("constraints.allowedOrganizationalUnitIds");
        assertThat(validator.validateConstraints(json.readTree("{\"roleName\": \"Admin\"}"), false))
                .extracting(FieldViolation::field).containsExactly("constraints.roleName");
        assertThat(validator.validateConstraints(constraints, true)).isEmpty();
    }
}
