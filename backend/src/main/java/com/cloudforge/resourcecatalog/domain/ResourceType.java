package com.cloudforge.resourcecatalog.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.cloudforge.common.persistence.BaseEntity;

/** A catalog entry. Rows are seeded by migration; administrators edit presentation and availability. */
@Entity
@Table(name = "resource_type")
public class ResourceType extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private ResourceTypeCode code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private CloudProvider provider;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    protected ResourceType() {
        // for JPA
    }

    public void describe(String newDisplayName, String newDescription) {
        this.displayName = Objects.requireNonNull(newDisplayName, "displayName").strip();
        this.description = Objects.requireNonNull(newDescription, "description").strip();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ResourceTypeCode getCode() {
        return code;
    }

    public CloudProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
