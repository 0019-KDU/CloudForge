package com.cloudforge.provisioning.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims due jobs for one worker in a single short transaction.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} lets any number of workers on any number of instances claim
 * concurrently: rows locked by another claimer are skipped instead of waited for, and each job is
 * claimed by exactly one worker. Jobs whose lease expired (their worker crashed) are claimable again.
 */
@Component
class ProvisioningJobClaimer {

    private static final String CLAIM = """
            UPDATE provisioning_job
               SET status = 'RUNNING', locked_by = ?, locked_until = ?, updated_at = ?, version = version + 1
             WHERE id IN (
                   SELECT id FROM provisioning_job
                    WHERE (status IN ('QUEUED', 'AWAITING_PROVIDER') AND next_run_at <= ?)
                       OR (status = 'RUNNING' AND locked_until < ?)
                    ORDER BY next_run_at
                    LIMIT ?
                      FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    ProvisioningJobClaimer(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claim(String workerId, int batchSize, Duration lease) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        Timestamp leaseUntil = Timestamp.from(now.toInstant().plus(lease));
        return jdbc.queryForList(CLAIM, UUID.class, workerId, leaseUntil, now, now, now, batchSize);
    }
}
