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
import com.cloudforge.config.JwtProperties;
import com.cloudforge.identity.domain.OpaqueToken;
import com.cloudforge.identity.domain.RefreshToken;
import com.cloudforge.identity.repository.RefreshTokenRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation with reuse detection.
 *
 * <p>Failures are committed, not rolled back ({@code noRollbackFor}): when a replayed token revokes
 * its family, that revocation must persist even though the request itself is rejected.
 */
@Service
@Transactional(noRollbackFor = AuthenticationFailedException.class)
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;
    private final AuditLog audit;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties, AuditLog audit, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    public record IssuedRefreshToken(OpaqueToken token, Instant expiresAt) {
    }

    public record Rotation(UUID userId, IssuedRefreshToken next) {
    }

    public IssuedRefreshToken startFamily(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    /** Consumes {@code presented} and returns its successor, or fails with 401. */
    public Rotation rotate(String presented) {
        Instant now = Instant.now(clock);
        RefreshToken current = find(presented).orElseThrow(RefreshTokenService::invalid);
        if (current.isReplayed()) {
            int revoked = tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected; revoked token family (userId={}, tokens={})",
                    current.getUserId(), revoked);
            audit.record(Actor.system(), AuditAction.REFRESH_TOKEN_REUSE_DETECTED, AuditEntityType.USER,
                    current.getUserId(), Map.of("revokedTokens", revoked));
            throw invalid();
        }
        if (!current.isUsable(now)) {
            throw invalid();
        }
        current.markRotated(now);
        tokens.saveAndFlush(current); // the version check makes a concurrent second rotation fail here
        return new Rotation(current.getUserId(), issue(current.getUserId(), current.getFamilyId()));
    }

    public void revokeFamilyOf(String presented) {
        find(presented).ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
    }

    public void revokeAllForUser(UUID userId) {
        tokens.revokeAllForUser(userId, Instant.now(clock));
    }

    private IssuedRefreshToken issue(UUID userId, UUID familyId) {
        OpaqueToken token = OpaqueToken.generate();
        Instant expiresAt = Instant.now(clock).plus(properties.refreshTokenTtl());
        tokens.save(new RefreshToken(userId, familyId, token.hash(), expiresAt));
        return new IssuedRefreshToken(token, expiresAt);
    }

    private Optional<RefreshToken> find(String presented) {
        if (presented == null || presented.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(new OpaqueToken(presented).hash());
    }

    private static AuthenticationFailedException invalid() {
        return new AuthenticationFailedException("INVALID_REFRESH_TOKEN", "The session has expired. Sign in again.");
    }
}
