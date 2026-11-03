package com.cloudforge.provisioning.domain;

import java.util.UUID;

/** Published when a provisioning job reaches a final state; consumed after commit (notifications). */
public final class ProvisioningEvents {

    private ProvisioningEvents() {
    }

    /**
     * @param message external resource ID on success, otherwise the sanitized failure message
     */
    public record ProvisioningFinished(UUID requestId, String requestNumber, UUID requesterId, boolean succeeded,
            String message) {
    }
}
