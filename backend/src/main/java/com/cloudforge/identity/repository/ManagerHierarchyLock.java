package com.cloudforge.identity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes changes to the manager hierarchy across all application instances.
 *
 * <p>The cycle check reads several rows and then updates a different one, so neither row locks
 * nor optimistic versions stop two concurrent reassignments (A→B and B→A) from both passing and
 * forming a cycle. A transaction-scoped PostgreSQL advisory lock closes that gap. It is released
 * automatically at commit or rollback, and hierarchy edits are rare admin actions, so the
 * contention cost is negligible.
 */
@Component
public class ManagerHierarchyLock {

    // Arbitrary application-wide constant identifying this lock.
    private static final long LOCK_KEY = 0x436C6F7564466F72L;

    private final JdbcTemplate jdbc;

    ManagerHierarchyLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, LOCK_KEY);
    }
}
