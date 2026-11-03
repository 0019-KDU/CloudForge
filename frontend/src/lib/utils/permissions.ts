import type { RequestResponse, Role, UserResponse } from "@/types/api";

/**
 * UI-level permission checks mirroring the backend's RequestAccessPolicy. They only decide what
 * to show; the backend enforces every rule again on each call.
 */

type Subject = Pick<UserResponse, "id" | "roles">;

export function hasRole(user: Subject | null | undefined, role: Role): boolean {
  return user?.roles.includes(role) ?? false;
}

export function hasAnyRole(user: Subject | null | undefined, roles: readonly Role[]): boolean {
  return roles.some((role) => hasRole(user, role));
}

/** Engineers and managers request resources; admins and auditors govern. */
export function canCreateRequests(user: Subject) {
  return hasAnyRole(user, ["ENGINEER", "MANAGER"]);
}

export function canViewAllRequests(user: Subject) {
  return hasAnyRole(user, ["PLATFORM_ADMIN", "AUDITOR"]);
}

export function isRequester(user: Subject, request: Pick<RequestResponse, "requesterId">) {
  return request.requesterId === user.id;
}

export function canEditDraft(user: Subject, request: Pick<RequestResponse, "requesterId" | "status">) {
  return isRequester(user, request) && request.status === "DRAFT";
}

export function canCancel(user: Subject, request: Pick<RequestResponse, "requesterId" | "status">) {
  return isRequester(user, request) && request.status === "PENDING_APPROVAL";
}

export function canDecide(
  user: Subject,
  request: Pick<RequestResponse, "requesterId" | "assignedApproverId" | "status">,
) {
  return (
    request.status === "PENDING_APPROVAL" &&
    request.assignedApproverId === user.id &&
    !isRequester(user, request) &&
    hasRole(user, "MANAGER")
  );
}

export function canRetryProvisioning(user: Subject, request: Pick<RequestResponse, "status">) {
  return hasRole(user, "PLATFORM_ADMIN") && request.status === "PROVISIONING_FAILED";
}
