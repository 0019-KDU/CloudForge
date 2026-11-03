package com.cloudforge.organization.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.cloudforge.organization.domain.Department;
import com.cloudforge.organization.domain.Team;

/** Request and response bodies of the organization admin API. */
final class OrganizationDtos {

    private OrganizationDtos() {
    }

    record DepartmentRequest(@NotBlank @Size(max = 100) String name) {
    }

    record DepartmentResponse(UUID id, String name, Instant createdAt, Instant updatedAt, Long version) {

        static DepartmentResponse from(Department department) {
            return new DepartmentResponse(department.getId(), department.getName(),
                    department.getCreatedAt(), department.getUpdatedAt(), department.getVersion());
        }
    }

    record CreateTeamRequest(@NotNull UUID departmentId, @NotBlank @Size(max = 100) String name) {
    }

    record RenameTeamRequest(@NotBlank @Size(max = 100) String name) {
    }

    record TeamResponse(UUID id, UUID departmentId, String name, Instant createdAt, Instant updatedAt, Long version) {

        static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getDepartmentId(), team.getName(),
                    team.getCreatedAt(), team.getUpdatedAt(), team.getVersion());
        }
    }
}
