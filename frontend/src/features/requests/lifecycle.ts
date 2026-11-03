import type { RequestResponse } from "@/types/api";

export type StepState = "done" | "current" | "failed" | "upcoming" | "skipped";

export interface Step {
  label: string;
  state: StepState;
}

/** Where a request is in Draft → Approval → Provisioning, derived from its status. */
export function lifecycleSteps(request: Pick<RequestResponse, "status">): Step[] {
  const s = request.status;
  const submitted: StepState = s === "DRAFT" ? "current" : "done";
  const decision: StepState =
    s === "DRAFT" ? "upcoming" : s === "PENDING_APPROVAL" ? "current" : s === "REJECTED" ? "failed" : s === "CANCELLED" ? "skipped" : "done";
  const provisioning: StepState =
    s === "APPROVED" || s === "PROVISIONING"
      ? "current"
      : s === "PROVISIONED"
        ? "done"
        : s === "PROVISIONING_FAILED"
          ? "failed"
          : s === "REJECTED" || s === "CANCELLED"
            ? "skipped"
            : "upcoming";
  const ready: StepState = s === "PROVISIONED" ? "done" : provisioning === "skipped" ? "skipped" : "upcoming";

  return [
    { label: s === "DRAFT" ? "Draft" : "Submitted", state: submitted },
    {
      label: s === "REJECTED" ? "Rejected" : s === "CANCELLED" ? "Cancelled" : s === "PENDING_APPROVAL" ? "Awaiting approval" : "Approved",
      state: decision,
    },
    { label: s === "PROVISIONING_FAILED" ? "Provisioning failed" : "Provisioning", state: provisioning },
    { label: "Ready", state: ready },
  ];
}
