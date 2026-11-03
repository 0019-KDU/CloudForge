package com.cloudforge.identity.domain;

/** Expiry is not a stored status: a {@code PENDING} invitation past {@code expiresAt} is expired. */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED
}
