package com.cloudforge.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, taken from the verified access token. Controllers receive it as a
 * method parameter and pass {@link #id()} to application services as the acting user. Services never
 * accept a requester or actor ID from the request body.
 *
 * @param id          the user ID ({@code sub} claim)
 * @param authorities granted authorities, for example {@code ROLE_MANAGER}
 */
public record CurrentUser(UUID id, Set<String> authorities) {

    public CurrentUser {
        authorities = Set.copyOf(authorities);
    }

    public boolean hasRole(String role) {
        return authorities.contains("ROLE_" + role);
    }
}
