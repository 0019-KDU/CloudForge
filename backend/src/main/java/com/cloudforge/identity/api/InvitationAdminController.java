package com.cloudforge.identity.api;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cloudforge.common.api.PageResponse;
import com.cloudforge.common.api.SortablePageRequest;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.api.IdentityDtos.CreateInvitationRequest;
import com.cloudforge.identity.api.IdentityDtos.InvitationResponse;
import com.cloudforge.identity.api.IdentityDtos.IssuedInvitationResponse;
import com.cloudforge.identity.application.InvitationService;
import com.cloudforge.identity.domain.InvitationStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invitations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class InvitationAdminController {

    private static final Set<String> SORTABLE = Set.of("email", "status", "expiresAt", "createdAt");

    private final InvitationService invitations;
    private final Clock clock;

    InvitationAdminController(InvitationService invitations, Clock clock) {
        this.invitations = invitations;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<IssuedInvitationResponse> invite(CurrentUser actor,
            @Valid @RequestBody CreateInvitationRequest request) {
        var issued = invitations.invite(actor.id(), request.email(), request.roles(), request.managerId(),
                request.teamId());
        // The body carries a one-time secret: no caching by browsers or intermediaries.
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(IssuedInvitationResponse.from(issued, Instant.now(clock)));
    }

    @GetMapping
    PageResponse<InvitationResponse> list(@RequestParam(required = false) InvitationStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Instant now = Instant.now(clock);
        return PageResponse.of(invitations.list(status,
                SortablePageRequest.restrict(pageable, SORTABLE)), i -> InvitationResponse.from(i, now));
    }

    @GetMapping("/{id}")
    InvitationResponse get(@PathVariable UUID id) {
        return InvitationResponse.from(invitations.require(id), Instant.now(clock));
    }

    @PostMapping("/{id}/revoke")
    InvitationResponse revoke(CurrentUser actor, @PathVariable UUID id) {
        return InvitationResponse.from(invitations.revoke(actor.id(), id), Instant.now(clock));
    }
}
