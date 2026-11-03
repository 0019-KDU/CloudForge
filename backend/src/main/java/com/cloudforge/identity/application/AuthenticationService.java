package com.cloudforge.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.AuthenticationFailedException;
import com.cloudforge.identity.application.AccessTokenIssuer.AccessToken;
import com.cloudforge.identity.application.RefreshTokenService.IssuedRefreshToken;
import com.cloudforge.identity.application.RefreshTokenService.Rotation;
import com.cloudforge.identity.domain.EmailAddress;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, token refresh and logout.
 *
 * <p>Account-discovery resistance: unknown email, wrong password, disabled and locked accounts all
 * produce the same error, and each path runs one bcrypt comparison so response times match.
 *
 * <p>Failed attempts must be persisted even though the call fails, hence {@code noRollbackFor}.
 */
@Service
@Transactional(noRollbackFor = AuthenticationFailedException.class)
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenService refreshTokens;
    private final AuditLog audit;
    private final Clock clock;
    private final String timingEqualizerHash;

    public AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokens, RefreshTokenService refreshTokens, AuditLog audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
        this.timingEqualizerHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record Tokens(AccessToken accessToken, IssuedRefreshToken refreshToken) {
    }

    public Tokens login(String email, String password) {
        Instant now = Instant.now(clock);
        Optional<User> candidate = users.findByEmail(EmailAddress.normalize(email));

        if (candidate.isEmpty() || !candidate.get().isActive() || candidate.get().isLocked(now)) {
            passwordEncoder.matches(password, timingEqualizerHash);
            candidate.ifPresent(user -> log.info("Login refused for inactive or locked account (userId={})", user.getId()));
            throw invalidCredentials();
        }
        User user = candidate.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean wasLocked = user.isLocked(now);
            user.recordFailedLogin(now);
            log.info("Login failed (userId={}, consecutiveFailures={})", user.getId(), user.getFailedLoginAttempts());
            audit.record(Actor.system(), AuditAction.LOGIN_FAILED, AuditEntityType.USER, user.getId(),
                    Map.of("consecutiveFailures", user.getFailedLoginAttempts()));
            if (!wasLocked && user.isLocked(now)) {
                audit.record(Actor.system(), AuditAction.ACCOUNT_LOCKED, AuditEntityType.USER, user.getId(),
                        Map.of("lockedUntil", user.getLockedUntil()));
            }
            throw invalidCredentials();
        }

        user.recordSuccessfulLogin();
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(password));
        }
        log.info("Login succeeded (userId={})", user.getId());
        audit.record(Actor.user(user.getId()), AuditAction.LOGIN_SUCCEEDED, AuditEntityType.USER, user.getId());
        return new Tokens(accessTokens.issue(user), refreshTokens.startFamily(user.getId()));
    }

    /** Rotates the refresh token and issues an access token with the user's current roles. */
    public Tokens refresh(String presentedRefreshToken) {
        Rotation rotation = refreshTokens.rotate(presentedRefreshToken);
        User user = users.findById(rotation.userId()).filter(User::isActive).orElse(null);
        if (user == null) {
            refreshTokens.revokeAllForUser(rotation.userId());
            throw new AuthenticationFailedException("INVALID_REFRESH_TOKEN", "The session has expired. Sign in again.");
        }
        return new Tokens(accessTokens.issue(user), rotation.next());
    }

    /** Idempotent: an unknown or already revoked token is simply ignored. */
    public void logout(String presentedRefreshToken) {
        refreshTokens.revokeFamilyOf(presentedRefreshToken);
    }

    private static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
}
