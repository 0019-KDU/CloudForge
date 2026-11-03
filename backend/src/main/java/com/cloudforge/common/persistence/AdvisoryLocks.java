package com.cloudforge.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped PostgreSQL advisory locks keyed by a string. Used where a check-then-act spans
 * rows that no single row lock covers, for example "count active requests, then submit one more".
 * Released automatically at commit or rollback; works across all application instances.
 */
@Component
public class AdvisoryLocks {

    private final JdbcTemplate jdbc;

    AdvisoryLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Blocks until the lock for {@code key} is held by the current transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, key);
    }
}
