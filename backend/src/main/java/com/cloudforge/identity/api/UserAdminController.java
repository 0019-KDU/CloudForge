package com.cloudforge.identity.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cloudforge.common.api.ETags;
import com.cloudforge.common.api.PageResponse;
import com.cloudforge.common.api.SortablePageRequest;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.api.IdentityDtos.AssignManagerRequest;
import com.cloudforge.identity.api.IdentityDtos.AssignTeamRequest;
import com.cloudforge.identity.api.IdentityDtos.ReplaceRolesRequest;
import com.cloudforge.identity.api.IdentityDtos.UserResponse;
import com.cloudforge.identity.application.PasswordResetService;
import com.cloudforge.identity.application.UserAdministrationService;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration. Each change is a separate business operation, not a generic PATCH, so each
 * one gets its own rules and later its own audit event.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class UserAdminController {

    private static final Set<String> SORTABLE = Set.of("email", "displayName", "status", "createdAt");

    private final UserAdministrationService users;
    private final PasswordResetService passwordResets;

    UserAdminController(UserAdministrationService users, PasswordResetService passwordResets) {
        this.users = users;
        this.passwordResets = passwordResets;
    }

    @GetMapping
    PageResponse<UserResponse> search(@RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UUID teamId, @RequestParam(required = false) UUID managerId,
            @PageableDefault(sort = "email", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(users.search(status, teamId, managerId,
                SortablePageRequest.restrict(pageable, SORTABLE)), UserResponse::from);
    }

    @GetMapping("/{id}")
    ResponseEntity<UserResponse> get(@PathVariable UUID id) {
        return withETag(users.requireUser(id));
    }

    // Updates accept an optional If-Match: "<version>" and answer 412 if the user changed meanwhile.

    @PutMapping("/{id}/manager")
    ResponseEntity<UserResponse> assignManager(CurrentUser actor, @PathVariable UUID id,
            @RequestBody AssignManagerRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withETag(users.assignManager(actor.id(), id, request.managerId(), ETags.parseIfMatch(ifMatch)));
    }

    @PutMapping("/{id}/team")
    ResponseEntity<UserResponse> assignTeam(CurrentUser actor, @PathVariable UUID id,
            @RequestBody AssignTeamRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withETag(users.assignTeam(actor.id(), id, request.teamId(), ETags.parseIfMatch(ifMatch)));
    }

    @PutMapping("/{id}/roles")
    ResponseEntity<UserResponse> replaceRoles(CurrentUser actor, @PathVariable UUID id,
            @Valid @RequestBody ReplaceRolesRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withETag(users.replaceRoles(actor.id(), id, request.roles(), ETags.parseIfMatch(ifMatch)));
    }

    @PostMapping("/{id}/disable")
    ResponseEntity<UserResponse> disable(CurrentUser actor, @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withETag(users.disable(actor.id(), id, ETags.parseIfMatch(ifMatch)));
    }

    @PostMapping("/{id}/enable")
    ResponseEntity<UserResponse> enable(CurrentUser actor, @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withETag(users.enable(actor.id(), id, ETags.parseIfMatch(ifMatch)));
    }

    record PasswordResetResponse(String token, Instant expiresAt) {
    }

    /**
     * Issues a one-time password reset token for a user who forgot their password. Verify the person
     * out of band first; hand them the token, which is shown only in this response.
     */
    @PostMapping("/{id}/password-reset")
    ResponseEntity<PasswordResetResponse> issuePasswordReset(CurrentUser actor, @PathVariable UUID id) {
        PasswordResetService.IssuedReset reset = passwordResets.issue(actor.id(), id);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new PasswordResetResponse(reset.token().value(), reset.expiresAt()));
    }

    private static ResponseEntity<UserResponse> withETag(User user) {
        return ResponseEntity.ok().eTag(ETags.of(user.getVersion())).body(UserResponse.from(user));
    }
}
