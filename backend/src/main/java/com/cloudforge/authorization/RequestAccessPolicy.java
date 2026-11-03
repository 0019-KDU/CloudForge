package com.cloudforge.authorization;

import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.common.security.CurrentUser;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may do what with a resource request. Role checks alone are not enough: every rule combines
 * the caller's roles with their relationship to the request (requester or assigned approver).
 *
 * <p>Information hiding: callers who may not see a request get {@code 404}, never {@code 403}, so
 * request IDs cannot be probed for existence. {@code 403} is used only when the caller can already
 * see the request but may not perform the operation.
 *
 * <p>The same rules exist as SQL filters in the request list queries; both are covered by tests.
 */
@Component
public class RequestAccessPolicy {

    static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    static final String AUDITOR = "AUDITOR";
    static final String MANAGER = "MANAGER";
    static final String ENGINEER = "ENGINEER";

    /** Requester, assigned approver, platform administrators and auditors. */
    public boolean canView(CurrentUser user, RequestParticipants request) {
        return isRequester(user, request) || isApprover(user, request)
                || user.hasRole(PLATFORM_ADMIN) || user.hasRole(AUDITOR);
    }

    public void requireView(CurrentUser user, RequestParticipants request) {
        if (!canView(user, request)) {
            throw notFound();
        }
    }

    /** Only engineers and managers create requests (administrators and auditors govern, not consume). */
    public void requireCanCreate(CurrentUser user) {
        if (!user.hasRole(ENGINEER) && !user.hasRole(MANAGER)) {
            throw new AccessDeniedException("Only engineers and managers can request resources");
        }
    }

    /** Editing a draft, submitting and cancelling: the requester only. */
    public void requireOwner(CurrentUser user, RequestParticipants request) {
        requireView(user, request);
        if (!isRequester(user, request)) {
            throw new AccessDeniedException("Only the requester can change this request");
        }
    }

    /**
     * Approving or rejecting: only the assigned approver, who must hold the MANAGER role and must not
     * be the requester. The self-approval check comes first and is explicit, and it holds even if
     * data were inconsistent (approver = requester), which the database also forbids.
     */
    public void requireDecider(CurrentUser user, RequestParticipants request) {
        requireView(user, request);
        if (isRequester(user, request)) {
            throw new BusinessRuleViolationException("SELF_APPROVAL_FORBIDDEN", "You cannot approve or reject your own request.");
        }
        if (!isApprover(user, request) || !user.hasRole(MANAGER)) {
            throw new AccessDeniedException("Only the assigned approver can decide this request");
        }
    }

    /** Triggering a provisioning retry is an operational action for platform administrators. */
    public void requireProvisioningOperator(CurrentUser user, RequestParticipants request) {
        requireView(user, request);
        if (!user.hasRole(PLATFORM_ADMIN)) {
            throw new AccessDeniedException("Only platform administrators can retry provisioning");
        }
    }

    public void requireListScope(CurrentUser user, RequestListScope scope) {
        if (scope == RequestListScope.ALL && !user.hasRole(PLATFORM_ADMIN) && !user.hasRole(AUDITOR)) {
            throw new AccessDeniedException("Listing all requests requires PLATFORM_ADMIN or AUDITOR");
        }
        if (scope == RequestListScope.ASSIGNED && !user.hasRole(MANAGER)) {
            throw new AccessDeniedException("Only managers have assigned requests");
        }
    }

    private static boolean isRequester(CurrentUser user, RequestParticipants request) {
        return user.id().equals(request.requesterId());
    }

    private static boolean isApprover(CurrentUser user, RequestParticipants request) {
        return request.approverId() != null && user.id().equals(request.approverId());
    }

    private static NotFoundException notFound() {
        return new NotFoundException("REQUEST_NOT_FOUND", "Request was not found.");
    }
}
