package com.cloudforge.organization.api;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cloudforge.common.api.ETags;
import com.cloudforge.common.api.PageResponse;
import com.cloudforge.common.api.SortablePageRequest;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.organization.api.OrganizationDtos.CreateTeamRequest;
import com.cloudforge.organization.api.OrganizationDtos.DepartmentRequest;
import com.cloudforge.organization.api.OrganizationDtos.DepartmentResponse;
import com.cloudforge.organization.api.OrganizationDtos.RenameTeamRequest;
import com.cloudforge.organization.api.OrganizationDtos.TeamResponse;
import com.cloudforge.organization.application.OrganizationService;
import com.cloudforge.organization.domain.Department;
import com.cloudforge.organization.domain.Team;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class OrganizationAdminController {

    private static final Set<String> SORTABLE = Set.of("name", "createdAt");

    private final OrganizationService organization;

    OrganizationAdminController(OrganizationService organization) {
        this.organization = organization;
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentResponse createDepartment(CurrentUser actor, @Valid @RequestBody DepartmentRequest request) {
        return DepartmentResponse.from(organization.createDepartment(actor.id(), request.name()));
    }

    @GetMapping("/departments")
    PageResponse<DepartmentResponse> listDepartments(
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(organization.listDepartments(
                SortablePageRequest.restrict(pageable, SORTABLE)), DepartmentResponse::from);
    }

    @GetMapping("/departments/{id}")
    ResponseEntity<DepartmentResponse> getDepartment(@PathVariable UUID id) {
        Department department = organization.requireDepartment(id);
        return ResponseEntity.ok().eTag(ETags.of(department.getVersion())).body(DepartmentResponse.from(department));
    }

    @PutMapping("/departments/{id}/name")
    ResponseEntity<DepartmentResponse> renameDepartment(CurrentUser actor, @PathVariable UUID id,
            @Valid @RequestBody DepartmentRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Department department = organization.renameDepartment(actor.id(), id, request.name(), ETags.parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(ETags.of(department.getVersion())).body(DepartmentResponse.from(department));
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    TeamResponse createTeam(CurrentUser actor, @Valid @RequestBody CreateTeamRequest request) {
        return TeamResponse.from(organization.createTeam(actor.id(), request.departmentId(), request.name()));
    }

    @GetMapping("/teams")
    PageResponse<TeamResponse> listTeams(@RequestParam(required = false) UUID departmentId,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(organization.listTeams(departmentId,
                SortablePageRequest.restrict(pageable, SORTABLE)), TeamResponse::from);
    }

    @GetMapping("/teams/{id}")
    ResponseEntity<TeamResponse> getTeam(@PathVariable UUID id) {
        Team team = organization.requireTeam(id);
        return ResponseEntity.ok().eTag(ETags.of(team.getVersion())).body(TeamResponse.from(team));
    }

    @PutMapping("/teams/{id}/name")
    ResponseEntity<TeamResponse> renameTeam(CurrentUser actor, @PathVariable UUID id,
            @Valid @RequestBody RenameTeamRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Team team = organization.renameTeam(actor.id(), id, request.name(), ETags.parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(ETags.of(team.getVersion())).body(TeamResponse.from(team));
    }
}
