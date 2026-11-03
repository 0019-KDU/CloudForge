package com.cloudforge.organization.repository;

import java.util.UUID;

import com.cloudforge.organization.domain.Team;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Page<Team> findByDepartmentId(UUID departmentId, Pageable pageable);

    boolean existsByDepartmentIdAndNameIgnoreCase(UUID departmentId, String name);

    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(UUID departmentId, String name, UUID id);
}
