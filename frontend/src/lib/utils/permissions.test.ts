import { describe, expect, it } from "vitest";

import { ids, users, withRoles } from "@/test/fixtures";
import type { RequestStatus } from "@/types/api";

import { canCancel, canCreateRequests, canDecide, canEditDraft, canRetryProvisioning, canViewAllRequests } from "./permissions";

const request = (status: RequestStatus, approverId: string | null = ids.manager) => ({
  requesterId: ids.engineer,
  assignedApproverId: approverId,
  status,
});

describe("permissions (UI mirror of RequestAccessPolicy)", () => {
  it("lets only engineers and managers create requests", () => {
    expect(canCreateRequests(users.engineer)).toBe(true);
    expect(canCreateRequests(users.manager)).toBe(true);
    expect(canCreateRequests(users.admin)).toBe(false);
    expect(canCreateRequests(users.auditor)).toBe(false);
  });

  it("limits the ALL scope to admins and auditors", () => {
    expect(canViewAllRequests(users.admin)).toBe(true);
    expect(canViewAllRequests(users.auditor)).toBe(true);
    expect(canViewAllRequests(users.manager)).toBe(false);
  });

  it("lets the requester edit drafts and cancel pending requests only", () => {
    expect(canEditDraft(users.engineer, request("DRAFT"))).toBe(true);
    expect(canEditDraft(users.engineer, request("PENDING_APPROVAL"))).toBe(false);
    expect(canEditDraft(users.manager, request("DRAFT"))).toBe(false);
    expect(canCancel(users.engineer, request("PENDING_APPROVAL"))).toBe(true);
    expect(canCancel(users.engineer, request("APPROVED"))).toBe(false);
    expect(canCancel(users.admin, request("PENDING_APPROVAL"))).toBe(false);
  });

  it("lets only the assigned manager decide a pending request", () => {
    expect(canDecide(users.manager, request("PENDING_APPROVAL"))).toBe(true);
    expect(canDecide(users.manager, request("APPROVED"))).toBe(false);
    expect(canDecide(users.admin, request("PENDING_APPROVAL", ids.admin))).toBe(false); // no MANAGER role
    expect(canDecide(withRoles(users.manager, ["MANAGER"]), request("PENDING_APPROVAL", ids.auditor))).toBe(false);
  });

  it("forbids self-approval even with inconsistent data", () => {
    const selfAssigned = { requesterId: ids.manager, assignedApproverId: ids.manager, status: "PENDING_APPROVAL" as const };
    expect(canDecide(users.manager, selfAssigned)).toBe(false);
  });

  it("lets only admins retry failed provisioning", () => {
    expect(canRetryProvisioning(users.admin, { status: "PROVISIONING_FAILED" })).toBe(true);
    expect(canRetryProvisioning(users.admin, { status: "PROVISIONING" })).toBe(false);
    expect(canRetryProvisioning(users.auditor, { status: "PROVISIONING_FAILED" })).toBe(false);
  });
});
