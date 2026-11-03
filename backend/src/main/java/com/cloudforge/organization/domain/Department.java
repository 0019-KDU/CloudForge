package com.cloudforge.organization.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.cloudforge.common.persistence.BaseEntity;

@Entity
@Table(name = "department")
public class Department extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    protected Department() {
        // for JPA
    }

    public Department(String name) {
        rename(name);
    }

    public void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "name").strip();
    }

    public String getName() {
        return name;
    }
}
