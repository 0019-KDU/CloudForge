package com.cloudforge.identity.domain;

/**
 * Platform roles. The set is fixed in code and mirrored by the {@code role} table for referential
 * integrity, so roles are an enum rather than an editable entity. Coarse permissions only:
 * ownership and manager relationships are checked separately by the authorization policies.
 */
public enum Role {
    ENGINEER,
    MANAGER,
    PLATFORM_ADMIN,
    AUDITOR
}
