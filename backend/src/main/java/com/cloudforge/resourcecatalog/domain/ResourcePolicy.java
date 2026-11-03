package com.cloudforge.resourcecatalog.domain;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.cloudforge.common.persistence.BaseEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Governance rules for one resource type in one environment.
 *
 * <p>{@code constraints} holds type-specific limits (for example allowed AWS organizational units or
 * a namespace CPU cap) as JSONB. It is stored as a JSON string and always checked by the type's
 * {@code ResourceSpecValidator} before saving, so the entity does not depend on a JSON library.
 */
@Entity
@Table(name = "resource_policy")
public class ResourcePolicy extends BaseEntity {

    @Column(name = "resource_type_id", nullable = false, updatable = false)
    private UUID resourceTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Environment environment;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "max_active_per_user")
    private Integer maxActivePerUser;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints", nullable = false)
    private String constraintsJson;

    protected ResourcePolicy() {
        // for JPA
    }

    public ResourcePolicy(UUID resourceTypeId, Environment environment) {
        this.resourceTypeId = Objects.requireNonNull(resourceTypeId, "resourceTypeId");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.allowed = false;
        this.constraintsJson = "{}";
    }

    public void update(boolean allowed, Integer maxActivePerUser, String constraintsJson) {
        if (maxActivePerUser != null && maxActivePerUser <= 0) {
            throw new IllegalArgumentException("maxActivePerUser must be positive");
        }
        this.allowed = allowed;
        this.maxActivePerUser = maxActivePerUser;
        this.constraintsJson = Objects.requireNonNull(constraintsJson, "constraintsJson");
    }

    public UUID getResourceTypeId() {
        return resourceTypeId;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Integer getMaxActivePerUser() {
        return maxActivePerUser;
    }

    public String getConstraintsJson() {
        return constraintsJson;
    }
}
