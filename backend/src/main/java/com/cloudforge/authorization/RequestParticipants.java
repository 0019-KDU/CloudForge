package com.cloudforge.authorization;

import java.util.Objects;
import java.util.UUID;

/**
 * The relationships of a request that access decisions depend on. Passed as plain IDs so the
 * authorization module does not depend on the request module's entities.
 *
 * @param approverId {@code null} while the request is a draft
 */
public record RequestParticipants(UUID requesterId, UUID approverId) {

    public RequestParticipants {
        Objects.requireNonNull(requesterId, "requesterId");
    }
}
