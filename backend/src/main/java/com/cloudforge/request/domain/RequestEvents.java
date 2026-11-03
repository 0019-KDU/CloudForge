package com.cloudforge.request.domain;

import java.util.UUID;

/**
 * Domain events published after request state changes. Listeners that must not affect the business
 * transaction (notifications) consume them after commit.
 */
public final class RequestEvents {

    private RequestEvents() {
    }

    public record RequestSubmitted(UUID requestId, String requestNumber, UUID requesterId, UUID approverId) {
    }

    public record RequestDecided(UUID requestId, String requestNumber, UUID requesterId, boolean approved,
            String comment) {
    }
}
