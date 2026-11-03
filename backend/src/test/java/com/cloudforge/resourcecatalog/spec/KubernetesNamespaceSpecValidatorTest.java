package com.cloudforge.resourcecatalog.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cloudforge.common.error.FieldViolation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class KubernetesNamespaceSpecValidatorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final KubernetesNamespaceSpecValidator validator = new KubernetesNamespaceSpecValidator();
    private final JsonNode limits = json.readTree("{\"maxCpuCores\": 4, \"maxMemoryGi\": 16}");

    private List<String> fieldsOf(String spec) {
        return validator.validateSpec(json.readTree(spec), limits).stream().map(FieldViolation::field).toList();
    }

    @Test
    void acceptsValidSpecWithinLimits() {
        assertThat(fieldsOf("{\"namespace\": \"team-a-dev\", \"cpuCores\": 0.5, \"memoryGi\": 16}")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Team-A", "-team", "team-", "team_a", "kube-system", "kube-anything", "default",
            "a234567890123456789012345678901234567890123456789012345678901234"})
    void rejectsInvalidOrReservedNamespaceNames(String name) {
        assertThat(fieldsOf("{\"namespace\": \"" + name + "\", \"cpuCores\": 1, \"memoryGi\": 1}"))
                .containsExactly("spec.namespace");
    }

    @Test
    void accepts63CharacterName() {
        String name = "a".repeat(63);
        assertThat(fieldsOf("{\"namespace\": \"" + name + "\", \"cpuCores\": 1, \"memoryGi\": 1}")).isEmpty();
    }

    @Test
    void enforcesPolicyLimitsAndPrecision() {
        assertThat(fieldsOf("{\"namespace\": \"ok\", \"cpuCores\": 4.001, \"memoryGi\": 17}"))
                .containsExactlyInAnyOrder("spec.cpuCores", "spec.memoryGi");
        assertThat(fieldsOf("{\"namespace\": \"ok\", \"cpuCores\": 0.0005, \"memoryGi\": 1}"))
                .containsExactly("spec.cpuCores");
        assertThat(fieldsOf("{\"namespace\": \"ok\", \"cpuCores\": -1, \"memoryGi\": 0}"))
                .containsExactlyInAnyOrder("spec.cpuCores", "spec.memoryGi");
    }

    @Test
    void reportsEveryMissingFieldAndRejectsUnknownFields() {
        assertThat(fieldsOf("{\"privileged\": true}"))
                .containsExactlyInAnyOrder("spec.privileged", "spec.namespace", "spec.cpuCores", "spec.memoryGi");
    }

    @Test
    void rejectsWrongTypes() {
        assertThat(fieldsOf("{\"namespace\": 42, \"cpuCores\": \"two\", \"memoryGi\": [1]}"))
                .containsExactlyInAnyOrder("spec.namespace", "spec.cpuCores", "spec.memoryGi");
        assertThat(validator.validateSpec(json.readTree("[]"), limits))
                .extracting(FieldViolation::field).contains("spec");
    }

    @Test
    void allowingPolicyMustSetBothLimits() {
        assertThat(validator.validateConstraints(json.readTree("{}"), true))
                .extracting(FieldViolation::field)
                .containsExactlyInAnyOrder("constraints.maxCpuCores", "constraints.maxMemoryGi");
        assertThat(validator.validateConstraints(json.readTree("{}"), false)).isEmpty();
        assertThat(validator.validateConstraints(limits, true)).isEmpty();
    }
}
