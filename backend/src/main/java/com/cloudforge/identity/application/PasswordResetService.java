package com.cloudforge.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.identity.domain.OpaqueToken;
import com.cloudforge.identity.domain.PasswordResetToken;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.repository.PasswordResetTokenRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" without an email channel: an administrator verifies the person out of band and
 * issues a one-time token (valid 24 hours, shown once, stored hashed); the user redeems it with a new
 * password. Issuing a token invalidates older ones; redeeming it also clears a login lockout and signs
 * out every existing session.
 */
@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final PasswordResetTokenRepository tokens;
    private final UserAdministrationService users;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditLog audit;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserAdministrationService users,
            PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
            AuditLog audit, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    public record IssuedReset(OpaqueToken token, Instant expiresAt) {
    }

    @Transactional
    public IssuedReset issue(UUID adminId, UUID userId) {
        users.requireActiveUser(adminId);
        User user = users.requireUser(userId);
        if (!user.isActive()) {
            throw new BusinessRuleViolationException("USER_DISABLED", "Enable the account before resetting its password.");
        }
        Instant now = Instant.now(clock);
        tokens.invalidateOpenTokens(userId, now);
        OpaqueToken token = OpaqueToken.generate();
        Instant expiresAt = now.plus(TOKEN_TTL);
        tokens.save(new PasswordResetToken(userId, token.hash(), adminId, expiresAt));
        audit.record(Actor.user(adminId), AuditAction.PASSWORD_RESET_ISSUED, AuditEntityType.USER, userId,
                Map.of("expiresAt", expiresAt));
        return new IssuedReset(token, expiresAt);
    }

    @Transactional
    public void redeem(String presentedToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken reset = tokens.findByTokenHash(new OpaqueToken(presentedToken).hash())
                .filter(candidate -> candidate.isRedeemable(now))
                .orElseThrow(() -> new BusinessRuleViolationException("RESET_TOKEN_INVALID",
                        "The reset link is invalid or has expired. Ask an administrator for a new one."));
        User user = users.requireUser(reset.getUserId());
        if (!user.isActive()) {
            throw new BusinessRuleViolationException("RESET_TOKEN_INVALID",
                    "The reset link is invalid or has expired. Ask an administrator for a new one.");
        }
        passwordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.recordSuccessfulLogin(); // clears a lockout caused by the forgotten password
        reset.markUsed(now);
        refreshTokens.revokeAllForUser(user.getId());
        audit.record(Actor.user(user.getId()), AuditAction.PASSWORD_RESET_COMPLETED, AuditEntityType.USER, user.getId());
    }
}
