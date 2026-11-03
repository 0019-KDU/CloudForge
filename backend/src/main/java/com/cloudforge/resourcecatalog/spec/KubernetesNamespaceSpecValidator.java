package com.cloudforge.resourcecatalog.spec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.cloudforge.common.error.FieldViolation;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Kubernetes namespace.
 *
 * <pre>
 * spec:        { "namespace": "team-a-dev", "cpuCores": 2, "memoryGi": 4 }
 * constraints: { "maxCpuCores": 8, "maxMemoryGi": 32 }
 * </pre>
 *
 * Namespace names must be RFC 1123 DNS labels (Kubernetes object-name rules): at most 63 characters,
 * lowercase alphanumerics or '-', starting and ending with an alphanumeric. Names starting with
 * {@code kube-} are reserved by Kubernetes, and the built-in {@code default} namespace cannot be
 * requested. CPU is limited to millicore precision (3 decimals).
 */
@Component
class KubernetesNamespaceSpecValidator implements ResourceSpecValidator {

    private static final Pattern DNS_LABEL = Pattern.compile("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$");
    private static final Set<String> RESERVED = Set.of("default");

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.K8S_NAMESPACE;
    }

    @Override
    public List<FieldViolation> validateSpec(JsonNode spec, JsonNode constraints) {
        List<FieldViolation> violations = new ArrayList<>();
        JsonFields fields = JsonFields.of(spec, "spec", violations)
                .allowOnly(Set.of("namespace", "cpuCores", "memoryGi"));

        String namespace = fields.requiredText("namespace", DNS_LABEL,
                "must be a lowercase DNS label: a-z, 0-9 and '-', at most 63 characters, starting and ending with a letter or digit");
        if (namespace != null && (namespace.startsWith("kube-") || RESERVED.contains(namespace))) {
            fields.violate("namespace", "is reserved by Kubernetes");
        }

        JsonFields limits = JsonFields.of(constraints, "constraints", new ArrayList<>());
        BigDecimal cpu = fields.requiredPositiveNumber("cpuCores", 3);
        BigDecimal maxCpu = limits.optionalPositiveNumber("maxCpuCores", 3);
        if (cpu != null && maxCpu != null && cpu.compareTo(maxCpu) > 0) {
            fields.violate("cpuCores", "must not exceed the policy limit of " + maxCpu.toPlainString());
        }
        BigDecimal memory = fields.requiredPositiveNumber("memoryGi", 3);
        BigDecimal maxMemory = limits.optionalPositiveNumber("maxMemoryGi", 3);
        if (memory != null && maxMemory != null && memory.compareTo(maxMemory) > 0) {
            fields.violate("memoryGi", "must not exceed the policy limit of " + maxMemory.toPlainString());
        }
        return violations;
    }

    @Override
    public List<FieldViolation> validateConstraints(JsonNode constraints, boolean allowed) {
        List<FieldViolation> violations = new ArrayList<>();
        JsonFields fields = JsonFields.of(constraints, "constraints", violations)
                .allowOnly(Set.of("maxCpuCores", "maxMemoryGi"));
        BigDecimal maxCpu = fields.optionalPositiveNumber("maxCpuCores", 3);
        BigDecimal maxMemory = fields.optionalPositiveNumber("maxMemoryGi", 3);
        if (allowed && maxCpu == null && !hasViolation(violations, "constraints.maxCpuCores")) {
            fields.violate("maxCpuCores", "is required when the policy allows requests");
        }
        if (allowed && maxMemory == null && !hasViolation(violations, "constraints.maxMemoryGi")) {
            fields.violate("maxMemoryGi", "is required when the policy allows requests");
        }
        return violations;
    }

    private static boolean hasViolation(List<FieldViolation> violations, String field) {
        return violations.stream().anyMatch(v -> v.field().equals(field));
    }
}
