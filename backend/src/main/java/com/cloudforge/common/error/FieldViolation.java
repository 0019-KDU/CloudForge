package com.cloudforge.common.error;

/**
 * One invalid input field, reported in the {@code errors} member of a problem response. The field is
 * a JSON path relative to the request body (for example {@code spec.namespace}). Rejected values are
 * never echoed, because they may be secrets.
 */
public record FieldViolation(String field, String message) {
}
