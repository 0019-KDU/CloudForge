package com.cloudforge.organization.domain;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.cloudforge.common.persistence.BaseEntity;

/**
 * A team inside a department. The department is referenced by ID rather than as an entity
 * association: a team never needs its department loaded, and plain IDs avoid lazy-loading traps.
 */
@Entity
@Table(name = "team")
public class Team extends BaseEntity {

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 100)
    private String name;

    protected Team() {
        // for JPA
    }

    public Team(UUID departmentId, String name) {
        this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
        rename(name);
    }

    public void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "name").strip();
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getName() {
        return name;
    }
}
