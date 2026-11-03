package com.cloudforge.common.maintenance;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes data that has no further value, so these tables do not grow without bound:
 * <ul>
 *   <li>refresh tokens expired for longer than the retention period (kept a while for incident analysis);</li>
 *   <li>notifications that were read longer ago than the retention period (unread ones are kept);</li>
 *   <li>login rate-limit windows older than a day.</li>
 * </ul>
 * Business history (requests, approvals, audit events, provisioning attempts) is never deleted here.
 *
 * <p>Plain bulk deletes are idempotent, so it is harmless when several instances run the job at the
 * same time; no distributed lock is needed.
 */
@Component
public class DataRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);

    /**
     * @param refreshTokenRetention how long expired refresh tokens are kept
     * @param notificationRetention how long read notifications are kept
     */
    @ConfigurationProperties("cloudforge.maintenance")
    public record Settings(@DefaultValue("P30D") Duration refreshTokenRetention,
            @DefaultValue("P90D") Duration notificationRetention) {
    }

    public record Result(int refreshTokens, int notifications, int throttleWindows) {
    }

    private final JdbcTemplate jdbc;
    private final Settings settings;
    private final Clock clock;

    DataRetentionJob(JdbcTemplate jdbc, Settings settings, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    /** Daily at 03:30 server time by default; disable with {@code cloudforge.maintenance.cron=-}. */
    @Scheduled(cron = "${cloudforge.maintenance.cron:0 30 3 * * *}")
    void scheduledRun() {
        run();
    }

    @Transactional
    public Result run() {
        Instant now = Instant.now(clock);
        int tokens = jdbc.update("DELETE FROM refresh_token WHERE expires_at < ?",
                Timestamp.from(now.minus(settings.refreshTokenRetention())));
        int notifications = jdbc.update("DELETE FROM notification WHERE read_at IS NOT NULL AND read_at < ?",
                Timestamp.from(now.minus(settings.notificationRetention())));
        int windows = jdbc.update("DELETE FROM login_throttle WHERE window_start < ?",
                Timestamp.from(now.minus(Duration.ofDays(1))));
        Result result = new Result(tokens, notifications, windows);
        log.info("Data retention cleanup removed {}", result);
        return result;
    }
}
