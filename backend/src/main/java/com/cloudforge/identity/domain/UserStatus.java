package com.cloudforge.identity.domain;

/** Users are never deleted, only disabled, so audit and approval history keeps resolving. */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
