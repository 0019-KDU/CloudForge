package com.cloudforge.common.error;

import java.util.List;

/**
 * A structurally valid request whose content breaks domain rules, reported field by field (422).
 * Used where Bean Validation cannot express the rules, for example provider-specific resource specs
 * checked against a policy.
 */
public class InvalidSpecificationException extends CloudForgeException {

    private final List<FieldViolation> violations;

    public InvalidSpecificationException(String code, String message, List<FieldViolation> violations) {
        super(code, message);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
