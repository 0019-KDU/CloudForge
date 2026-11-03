package com.cloudforge.resourcecatalog.spec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.cloudforge.common.error.FieldViolation;

import tools.jackson.databind.JsonNode;

/**
 * Field-by-field reader for an untrusted JSON object. Every problem is collected as a
 * {@link FieldViolation} (with the full path, for example {@code spec.namespace}) instead of failing
 * on the first error, so a client can fix all fields in one round trip.
 */
final class JsonFields {

    private final JsonNode node;
    private final String path;
    private final List<FieldViolation> violations;

    private JsonFields(JsonNode node, String path, List<FieldViolation> violations) {
        this.node = node;
        this.path = path;
        this.violations = violations;
    }

    /** {@code null} or a missing node is treated as an empty object. */
    static JsonFields of(JsonNode node, String path, List<FieldViolation> violations) {
        if (node != null && !node.isNull() && !node.isMissingNode() && !node.isObject()) {
            violations.add(new FieldViolation(path, "must be a JSON object"));
            return new JsonFields(null, path, violations);
        }
        return new JsonFields(node == null || node.isNull() || node.isMissingNode() ? null : node, path, violations);
    }

    /** Unknown fields are rejected so nothing unvalidated can be passed through to a provider. */
    JsonFields allowOnly(Set<String> names) {
        if (node != null) {
            for (String name : node.propertyNames()) {
                if (!names.contains(name)) {
                    violate(name, "is not a supported field");
                }
            }
        }
        return this;
    }

    String requiredText(String name, Pattern pattern, String formatMessage) {
        String value = optionalText(name, pattern, formatMessage);
        if (value == null && isAbsent(name)) {
            violate(name, "is required");
        }
        return value;
    }

    String optionalText(String name, Pattern pattern, String formatMessage) {
        JsonNode value = field(name);
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            violate(name, "must be a string");
            return null;
        }
        String text = value.stringValue();
        if (pattern != null && !pattern.matcher(text).matches()) {
            violate(name, formatMessage);
            return null;
        }
        return text;
    }

    /** A positive decimal with at most {@code maxScale} fractional digits. */
    BigDecimal requiredPositiveNumber(String name, int maxScale) {
        BigDecimal value = optionalPositiveNumber(name, maxScale);
        if (value == null && isAbsent(name)) {
            violate(name, "is required");
        }
        return value;
    }

    BigDecimal optionalPositiveNumber(String name, int maxScale) {
        JsonNode value = field(name);
        if (value == null) {
            return null;
        }
        if (!value.isNumber()) {
            violate(name, "must be a number");
            return null;
        }
        BigDecimal number = value.decimalValue().stripTrailingZeros();
        if (number.signum() <= 0) {
            violate(name, "must be greater than zero");
            return null;
        }
        if (number.scale() > maxScale) {
            violate(name, "must have at most " + maxScale + " decimal places");
            return null;
        }
        return number;
    }

    /** A list of strings each matching {@code pattern}; returns an empty list when absent. */
    List<String> optionalTextList(String name, Pattern pattern, String formatMessage) {
        JsonNode value = field(name);
        if (value == null) {
            return List.of();
        }
        if (!value.isArray()) {
            violate(name, "must be an array of strings");
            return List.of();
        }
        List<String> items = new ArrayList<>();
        int index = 0;
        for (JsonNode item : value.values()) {
            if (!item.isString() || !pattern.matcher(item.stringValue()).matches()) {
                violate(name + "[" + index + "]", formatMessage);
            } else {
                items.add(item.stringValue());
            }
            index++;
        }
        return items;
    }

    void violate(String name, String message) {
        violations.add(new FieldViolation(path + "." + name, message));
    }

    private boolean isAbsent(String name) {
        return node == null || node.get(name) == null || node.get(name).isNull();
    }

    private JsonNode field(String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value;
    }
}
