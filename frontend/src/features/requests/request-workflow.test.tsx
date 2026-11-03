import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { makeRequest, useFakeBackend } from "@/test/fake-backend";
import { ids, users } from "@/test/fixtures";
import { renderApp } from "@/test/render";

describe("engineer: creating a request", () => {
  it("walks through type, configuration, justification and review, then submits", async () => {
    const db = useFakeBackend(users.engineer);
    const { user, router } = renderApp("/requests/new");

    await user.click(await screen.findByRole("button", { name: /Kubernetes namespace/ }));
    await user.click(screen.getByRole("radio", { name: "Development" }));
    await user.type(screen.getByLabelText("Namespace"), "payments-dev");
    await user.type(screen.getByLabelText("CPU quota"), "2");
    await user.type(screen.getByLabelText("Memory quota"), "4");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    await user.type(await screen.findByLabelText("Business justification"), "Integration testing for the payments service.");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    // Review shows exactly what will be sent.
    expect(await screen.findByText("payments-dev")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Submit for approval" }));

    await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/requests\/[0-9a-f-]+$/));
    expect(await screen.findByText("Pending approval", { selector: "span" })).toBeInTheDocument();

    const create = db.calls.find((call) => call.method === "POST" && call.path === "/api/requests");
    expect(create?.body).toEqual({
      resourceType: "K8S_NAMESPACE",
      environment: "DEVELOPMENT",
      spec: { namespace: "payments-dev", cpuCores: 2, memoryGi: 4 },
      justification: "Integration testing for the payments service.",
    });
    expect(db.calls.some((call) => call.path.endsWith("/submit"))).toBe(true);
  });

  it("validates per type and environment before calling the server", async () => {
    const db = useFakeBackend(users.engineer);
    const { user } = renderApp("/requests/new?type=K8S_NAMESPACE");

    await user.click(await screen.findByRole("radio", { name: "Staging" }));
    await user.type(screen.getByLabelText("Namespace"), "kube-system");
    await user.type(screen.getByLabelText("CPU quota"), "6");
    await user.type(screen.getByLabelText("Memory quota"), "1.2345");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("This name is reserved by Kubernetes.")).toBeInTheDocument();
    // Staging's policy caps CPU at 4.
    expect(screen.getByText("The policy allows at most 4.")).toBeInTheDocument();
    expect(screen.getByText("Use at most 3 decimal places.")).toBeInTheDocument();
    expect(db.calls.some((call) => call.path.includes("spec-validation"))).toBe(false);
  });

  it("shows the server's spec violations on the matching field", async () => {
    useFakeBackend(users.engineer);
    const { user } = renderApp("/requests/new?type=K8S_NAMESPACE");

    await user.click(await screen.findByRole("radio", { name: "Development" }));
    await user.type(screen.getByLabelText("Namespace"), "taken-by-policy");
    await user.type(screen.getByLabelText("CPU quota"), "1");
    await user.type(screen.getByLabelText("Memory quota"), "1");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Is not allowed by the policy")).toBeInTheDocument();
    expect(screen.getByLabelText("Namespace")).toHaveAttribute("aria-invalid", "true");
  });

  it("keeps the work as a draft and explains when submission is refused", async () => {
    const db = useFakeBackend({ ...users.engineer, managerId: null });
    const { user } = renderApp("/requests/new?type=K8S_NAMESPACE");

    await user.click(await screen.findByRole("radio", { name: "Development" }));
    await user.type(screen.getByLabelText("Namespace"), "orphan-ns");
    await user.type(screen.getByLabelText("CPU quota"), "1");
    await user.type(screen.getByLabelText("Memory quota"), "1");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(await screen.findByLabelText("Business justification"), "Testing without a manager assigned.");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(await screen.findByRole("button", { name: "Submit for approval" }));

    expect(await screen.findByText(/was saved as a draft but not submitted/)).toBeInTheDocument();
    expect(screen.getByText(/You have no manager to approve this request/)).toBeInTheDocument();
    expect(db.requests[0]?.status).toBe("DRAFT");
    expect(await screen.findByRole("button", { name: "Submit for approval" })).toBeInTheDocument();
  });

  it("offers only resource types that some environment allows", async () => {
    useFakeBackend(users.engineer);
    renderApp("/requests/new");

    expect(await screen.findByRole("button", { name: /GCP project/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /AWS account/ })).toBeEnabled();
  });
});

describe("engineer: request status", () => {
  it("lists own requests with server-side filters", async () => {
    const db = useFakeBackend(users.engineer, {
      requests: [makeRequest({ status: "PROVISIONED" }), makeRequest({ status: "PENDING_APPROVAL" })],
    });
    renderApp("/requests?status=PROVISIONED");

    const table = await screen.findByRole("table", { name: "My requests" });
    expect(within(table).getAllByRole("row")).toHaveLength(2); // header + one match
    expect(within(table).getByText("Provisioned")).toBeInTheDocument();
    const list = db.calls.find((call) => call.path === "/api/requests" && call.method === "GET");
    expect(list?.search).toContain("scope=MINE");
    expect(list?.search).toContain("status=PROVISIONED");
  });

  it("shows provisioning failures with their reason", async () => {
    const failed = makeRequest({ status: "PROVISIONING_FAILED" });
    const db = useFakeBackend(users.engineer, { requests: [failed] });
    db.provisioning.set(failed.id, {
      status: "FAILED",
      attemptCount: 5,
      nextRunAt: null,
      lastErrorCode: "QUOTA_UNAVAILABLE",
      lastErrorMessage: "The cluster has no capacity for this quota.",
      awaitingProvider: false,
      result: null,
      attempts: [
        {
          attemptNumber: 5,
          kind: "START",
          outcome: "PERMANENT_FAILURE",
          startedAt: "2026-10-30T12:00:00Z",
          finishedAt: "2026-10-30T12:00:05Z",
          errorCode: "QUOTA_UNAVAILABLE",
          errorMessage: "The cluster has no capacity for this quota.",
        },
      ],
    });
    renderApp(`/requests/${failed.id}`);

    expect(await screen.findByText("Provisioning failed", { selector: "p" })).toBeInTheDocument();
    expect(screen.getAllByText("The cluster has no capacity for this quota.").length).toBeGreaterThan(0);
    // Only administrators may retry.
    expect(screen.queryByRole("button", { name: "Retry provisioning" })).not.toBeInTheDocument();
  });

  it("shows the created resource once provisioned", async () => {
    const done = makeRequest({ status: "PROVISIONED" });
    const db = useFakeBackend(users.engineer, { requests: [done] });
    db.provisioning.set(done.id, {
      status: "SUCCEEDED",
      attemptCount: 1,
      nextRunAt: null,
      lastErrorCode: null,
      lastErrorMessage: null,
      awaitingProvider: false,
      result: { externalResourceId: "ns-uid-1234", details: { namespace: "team-a" }, createdAt: "2026-10-30T12:05:00Z" },
      attempts: [],
    });
    renderApp(`/requests/${done.id}`);

    expect(await screen.findByText("ns-uid-1234")).toBeInTheDocument();
    expect(screen.getByText("Created resource")).toBeInTheDocument();
  });

  it("lets the requester cancel a pending request after confirming", async () => {
    const pending = makeRequest();
    const db = useFakeBackend(users.engineer, { requests: [pending] });
    const { user } = renderApp(`/requests/${pending.id}`);

    await user.click(await screen.findByRole("button", { name: "Cancel request" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Cancel request" }));

    await waitFor(() => expect(db.requests[0]?.status).toBe("CANCELLED"));
    // Shown in the status badge and in the lifecycle stepper.
    expect(await screen.findAllByText("Cancelled")).not.toHaveLength(0);
    expect(screen.queryByRole("button", { name: "Cancel request" })).not.toBeInTheDocument();
  });

  it("treats requests the backend hides as not found", async () => {
    useFakeBackend(users.engineer, { requests: [makeRequest({ requesterId: ids.admin, assignedApproverId: ids.auditor })] });
    renderApp("/requests/0192f0a0-0000-7000-8000-999999999999");

    expect(await screen.findByText("Not found")).toBeInTheDocument();
  });
});
