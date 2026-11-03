package com.cloudforge.support;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.application.AccessTokenIssuer;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.repository.UserRepository;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Creates real users in the test database and signs real access tokens for them, so integration
 * tests exercise the production JWT path rather than a mocked security context.
 */
@TestComponent
public class TestUsers {

    public static final String PASSWORD = "correct horse battery staple";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;

    public TestUsers(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
    }

    /** A new active user with a unique email and the password {@link #PASSWORD}. */
    public User create(Role... roles) {
        String email = roles[0].name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@example.com";
        return users.save(new User(email, email, passwordEncoder.encode(PASSWORD), Set.of(roles)));
    }

    /** A new active user whose manager is {@code manager}. */
    public User createReportingTo(User manager, Role... roles) {
        User user = create(roles);
        user.assignManager(manager.getId());
        return users.save(user);
    }

    /** The {@link CurrentUser} a verified token for {@code user} would produce. */
    public CurrentUser principal(User user) {
        return new CurrentUser(user.getId(),
                user.getRoles().stream().map(role -> "ROLE_" + role.name()).collect(Collectors.toSet()));
    }

    public RequestPostProcessor bearer(User user) {
        String token = accessTokens.issue(user).value();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
