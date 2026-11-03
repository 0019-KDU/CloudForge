package com.cloudforge.request.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Request lifecycle. The allowed transitions are defined here and nowhere else; every status change
 * goes through {@link ResourceRequest}, which checks them. See docs/architecture/request-lifecycle.md.
 */
public enum RequestStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED,
    PROVISIONING,
    PROVISIONED,
    PROVISIONING_FAILED;

    /** Statuses that count against a user's quota: anything submitted and not closed or rejected. */
    public static final Set<RequestStatus> ACTIVE =
            EnumSet.of(PENDING_APPROVAL, APPROVED, PROVISIONING, PROVISIONED, PROVISIONING_FAILED);

    public boolean canTransitionTo(RequestStatus target) {
        return switch (this) {
            case DRAFT -> target == PENDING_APPROVAL;
            case PENDING_APPROVAL -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED -> target == PROVISIONING;
            case PROVISIONING -> target == PROVISIONED || target == PROVISIONING_FAILED;
            case PROVISIONING_FAILED -> target == PROVISIONING;
            case REJECTED, CANCELLED, PROVISIONED -> false;
        };
    }
}
