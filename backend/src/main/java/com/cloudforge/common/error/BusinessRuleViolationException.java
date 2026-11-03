package com.cloudforge.common.error;

/**
 * The request is well formed but breaks a business rule, for example an illegal state transition
 * or a self-approval attempt.
 */
public class BusinessRuleViolationException extends CloudForgeException {

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
