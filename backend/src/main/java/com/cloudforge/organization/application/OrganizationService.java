package com.cloudforge.organization.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.ConflictException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.organization.domain.Department;
import com.cloudforge.organization.domain.Team;
import com.cloudforge.organization.repository.DepartmentRepository;
import com.cloudforge.organization.repository.TeamRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Departments and teams. Also the public API other modules use to check organization data, for
 * example identity verifying that a team exists before assigning a user to it.
 *
 * <p>Name uniqueness is checked up front for a friendly error code. The database unique indexes
 * stay the real guarantee under concurrency: a race loser gets the generic {@code DATA_CONFLICT}.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final DepartmentRepository departments;
    private final TeamRepository teams;
    private final AuditLog audit;

    public OrganizationService(DepartmentRepository departments, TeamRepository teams, AuditLog audit) {
        this.departments = departments;
        this.teams = teams;
        this.audit = audit;
    }

    @Transactional
    public Department createDepartment(UUID actorId, String name) {
        if (departments.existsByNameIgnoreCase(name.strip())) {
            throw duplicateDepartment();
        }
        Department department = departments.save(new Department(name));
        audit.record(Actor.user(actorId), AuditAction.DEPARTMENT_CREATED, AuditEntityType.DEPARTMENT,
                department.getId(), Map.of("name", department.getName()));
        return department;
    }

    @Transactional
    public Department renameDepartment(UUID actorId, UUID departmentId, String name, Long expectedVersion) {
        Department department = requireDepartment(departmentId);
        department.requireVersion(expectedVersion);
        if (departments.existsByNameIgnoreCaseAndIdNot(name.strip(), departmentId)) {
            throw duplicateDepartment();
        }
        department.rename(name);
        audit.record(Actor.user(actorId), AuditAction.DEPARTMENT_RENAMED, AuditEntityType.DEPARTMENT, departmentId,
                Map.of("name", department.getName()));
        return department;
    }

    public Page<Department> listDepartments(Pageable pageable) {
        return departments.findAll(pageable);
    }

    public Department requireDepartment(UUID departmentId) {
        return departments.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("DEPARTMENT_NOT_FOUND", "Department was not found."));
    }

    @Transactional
    public Team createTeam(UUID actorId, UUID departmentId, String name) {
        requireDepartment(departmentId);
        if (teams.existsByDepartmentIdAndNameIgnoreCase(departmentId, name.strip())) {
            throw duplicateTeam();
        }
        Team team = teams.save(new Team(departmentId, name));
        audit.record(Actor.user(actorId), AuditAction.TEAM_CREATED, AuditEntityType.TEAM, team.getId(),
                Map.of("name", team.getName(), "departmentId", departmentId));
        return team;
    }

    @Transactional
    public Team renameTeam(UUID actorId, UUID teamId, String name, Long expectedVersion) {
        Team team = requireTeam(teamId);
        team.requireVersion(expectedVersion);
        if (teams.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(team.getDepartmentId(), name.strip(), teamId)) {
            throw duplicateTeam();
        }
        team.rename(name);
        audit.record(Actor.user(actorId), AuditAction.TEAM_RENAMED, AuditEntityType.TEAM, teamId,
                Map.of("name", team.getName()));
        return team;
    }

    public Page<Team> listTeams(UUID departmentId, Pageable pageable) {
        if (departmentId == null) {
            return teams.findAll(pageable);
        }
        return teams.findByDepartmentId(departmentId, pageable);
    }

    /** Batch lookups for read models (for example the user directory): one query each. */
    public Map<UUID, Team> teamsById(Collection<UUID> teamIds) {
        return teams.findAllById(teamIds).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    public Map<UUID, Department> departmentsById(Collection<UUID> departmentIds) {
        return departments.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
    }

    public Team requireTeam(UUID teamId) {
        return teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("TEAM_NOT_FOUND", "Team was not found."));
    }

    private static ConflictException duplicateDepartment() {
        return new ConflictException("DEPARTMENT_NAME_TAKEN", "A department with this name already exists.");
    }

    private static ConflictException duplicateTeam() {
        return new ConflictException("TEAM_NAME_TAKEN", "A team with this name already exists in the department.");
    }
}
