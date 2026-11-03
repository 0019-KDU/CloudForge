package com.cloudforge.audit.domain;

import java.util.Objects;
import java.util.UUID;

/** Who performed an audited action: an authenticated user, or the platform itself (worker, scheduler). */
public record Actor(ActorType type, UUID userId) {

    public enum ActorType {
        USER,
        SYSTEM
    }

    public Actor {
        Objects.requireNonNull(type, "type");
        if ((type == ActorType.USER) != (userId != null)) {
            throw new IllegalArgumentException("A USER actor needs a user ID and a SYSTEM actor must not have one");
        }
    }

    public static Actor user(UUID userId) {
        return new Actor(ActorType.USER, Objects.requireNonNull(userId, "userId"));
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }
}
