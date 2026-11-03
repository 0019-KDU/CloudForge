package com.cloudforge.common.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import com.cloudforge.common.error.PreconditionFailedException;

import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Common identity, timestamps and optimistic locking for mutable aggregates.
 *
 * <p>IDs are time-ordered UUIDv7 values: they keep B-tree inserts local like a sequence would, but
 * cannot be guessed or enumerated. Hibernate assigns them during {@code persist}, so they are
 * available right after {@code save()}, before the transaction commits. Timestamps come from the
 * application {@link java.time.Clock} (see {@code JpaConfig}), and {@link #version} turns
 * concurrent updates of the same row into an {@code OptimisticLockingFailureException}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Fails with 412 when the client expected a different version (HTTP {@code If-Match}).
     * {@code null} means the client sent no precondition.
     */
    public void requireVersion(Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(version)) {
            throw new PreconditionFailedException(
                    "The resource was modified by someone else (current version " + version + "). Reload and retry.");
        }
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id != null && id.equals(((BaseEntity) other).id);
    }

    @Override
    public final int hashCode() {
        // Constant per class: stays stable when the ID is assigned on persist.
        return Hibernate.getClass(this).hashCode();
    }
}
