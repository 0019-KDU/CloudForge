import { humanize } from "@/lib/utils/labels";
import type { AuditAction } from "@/types/api";

const ACTION_LABELS: Record<AuditAction, string> = {
  REQUEST_CREATED: "Request created",
  REQUEST_UPDATED: "Draft edited",
  REQUEST_DELETED: "Draft deleted",
  REQUEST_SUBMITTED: "Submitted for approval",
  REQUEST_CANCELLED: "Request cancelled",
  REQUEST_APPROVED: "Approved",
  REQUEST_REJECTED: "Rejected",
  PROVISIONING_QUEUED: "Provisioning queued",
  PROVISIONING_STARTED: "Provisioning started",
  PROVISIONING_ATTEMPT_FAILED: "Provisioning attempt failed",
  PROVISIONING_SUCCEEDED: "Resource provisioned",
  PROVISIONING_FAILED: "Provisioning failed",
  PROVISIONING_RETRY_REQUESTED: "Provisioning retry requested",
  USER_REGISTERED: "User registered",
  USER_MANAGER_CHANGED: "Manager changed",
  USER_TEAM_CHANGED: "Team changed",
  USER_ROLES_CHANGED: "Roles changed",
  USER_DISABLED: "User disabled",
  USER_ENABLED: "User enabled",
  PROFILE_UPDATED: "Profile updated",
  PASSWORD_CHANGED: "Password changed",
  PASSWORD_RESET_ISSUED: "Password reset issued",
  PASSWORD_RESET_COMPLETED: "Password reset completed",
  INVITATION_CREATED: "Invitation created",
  INVITATION_REVOKED: "Invitation revoked",
  LOGIN_SUCCEEDED: "Signed in",
  LOGIN_FAILED: "Sign-in failed",
  ACCOUNT_LOCKED: "Account locked",
  REFRESH_TOKEN_REUSE_DETECTED: "Session token reuse detected",
  DEPARTMENT_CREATED: "Department created",
  DEPARTMENT_RENAMED: "Department renamed",
  TEAM_CREATED: "Team created",
  TEAM_RENAMED: "Team renamed",
  RESOURCE_TYPE_UPDATED: "Resource type updated",
  RESOURCE_POLICY_UPDATED: "Resource policy updated",
};

/** Unknown future actions are shown humanized instead of failing. */
export function auditActionLabel(action: string): string {
  return (ACTION_LABELS as Record<string, string | undefined>)[action] ?? humanize(action);
}

export type ActionTone = "neutral" | "success" | "danger" | "warning" | "info";

export function auditActionTone(action: string): ActionTone {
  if (/(APPROVED|SUCCEEDED|ENABLED|COMPLETED)$/.test(action)) return "success";
  if (/(REJECTED|FAILED|LOCKED|REUSE_DETECTED|DISABLED)$/.test(action) || action.endsWith("ATTEMPT_FAILED")) return "danger";
  if (/(CANCELLED|DELETED|REVOKED)$/.test(action)) return "warning";
  if (action.startsWith("PROVISIONING")) return "info";
  return "neutral";
}
