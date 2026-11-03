package com.cloudforge.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.cloudforge.common.error.TooManyRequestsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-client rate limit for the authentication endpoints, complementing the per-account lockout:
 * the lockout stops guessing one account's password, this stops one client from trying many
 * accounts (credential stuffing) or many invitation tokens.
 *
 * <p>Fixed window kept in PostgreSQL with one atomic upsert per attempt, so the limit is shared by
 * all instances. Every attempt counts, successful or not; legitimate users stay far below the limit.
 *
 * <p>The client key is the TCP peer address. Behind a reverse proxy, configure
 * {@code server.forward-headers-strategy} for the trusted proxy so the real client address is used.
 * {@code X-Forwarded-For} is never read directly because clients can forge it.
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private static final String COUNT_ATTEMPT = """
            INSERT INTO login_throttle (client_key, window_start, attempts) VALUES (?, ?, 1)
            ON CONFLICT (client_key) DO UPDATE SET
                attempts     = CASE WHEN login_throttle.window_start <= ? THEN 1 ELSE login_throttle.attempts + 1 END,
                window_start = CASE WHEN login_throttle.window_start <= ? THEN EXCLUDED.window_start
                                    ELSE login_throttle.window_start END
            RETURNING attempts, window_start
            """;

    /**
     * @param maxAttempts attempts allowed per client within one window
     * @param window      length of the window
     */
    @ConfigurationProperties("cloudforge.security.login-throttle")
    public record Settings(@DefaultValue("20") int maxAttempts, @DefaultValue("PT5M") Duration window) {
    }

    private final JdbcTemplate jdbc;
    private final Settings settings;
    private final Clock clock;

    public LoginThrottle(JdbcTemplate jdbc, Settings settings, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Counts one attempt for {@code clientKey}, in its own transaction so the count is kept even when
     * the authentication that follows fails, and rejects it when the window's limit is exceeded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = TooManyRequestsException.class)
    public void registerAttempt(String endpoint, String clientKey) {
        Instant now = Instant.now(clock);
        Timestamp expiredBefore = Timestamp.from(now.minus(settings.window()));
        Map<String, Object> row = jdbc.queryForMap(COUNT_ATTEMPT, endpoint + ":" + truncate(clientKey),
                Timestamp.from(now), expiredBefore, expiredBefore);
        int attempts = ((Number) row.get("attempts")).intValue();
        if (attempts > settings.maxAttempts()) {
            Instant windowStart = ((Timestamp) row.get("window_start")).toInstant();
            Duration retryAfter = Duration.between(now, windowStart.plus(settings.window()));
            if (attempts == settings.maxAttempts() + 1) {
                log.warn("Rate limit reached for {} from client {}", endpoint, clientKey);
            }
            throw new TooManyRequestsException("TOO_MANY_ATTEMPTS",
                    "Too many attempts. Try again later.", retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
    }

    private static String truncate(String clientKey) {
        String key = clientKey == null ? "unknown" : clientKey;
        return key.length() <= 80 ? key : key.substring(0, 80);
    }
}
