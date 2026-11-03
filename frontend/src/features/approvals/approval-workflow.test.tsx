import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { makeRequest, useFakeBackend } from "@/test/fake-backend";
import { ids, users } from "@/test/fixtures";
import { renderApp } from "@/test/render";

describe("manager: approvals", () => {
  it("lists requests assigned for approval with the requester's name", async () => {
    const db = useFakeBackend(users.manager, {
      requests: [makeRequest(), makeRequest({ assignedApproverId: ids.admin })],
    });
    renderApp("/approvals");

    const table = await screen.findByRole("table", { name: "Pending approvals" });
    expect(within(table).getAllByRole("row")).toHaveLength(2);
    expect(await within(table).findByText("Erin Engineer")).toBeInTheDocument();
    const call = db.calls.find((c) => c.path === "/api/requests" && c.method === "GET");
    expect(call?.search).toContain("scope=ASSIGNED");
    expect(call?.search).toContain("status=PENDING_APPROVAL");
  });

  it("approves after confirmation, then shows the new status", async () => {
    const request = makeRequest();
    const db = useFakeBackend(users.manager, { requests: [request] });
    const { user } = renderApp(`/requests/${request.id}`);

    await user.type(await screen.findByLabelText("Comment"), "Looks good.");
    await user.click(screen.getByRole("button", { name: "Approve" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Approve request" }));

    await waitFor(() => expect(db.requests[0]?.status).toBe("APPROVED"));
    expect(db.calls.filter((c) => c.path.endsWith("/approve"))).toHaveLength(1);
    expect(db.calls.find((c) => c.path.endsWith("/approve"))?.body).toEqual({ comment: "Looks good." });
    // The decision panel disappears and the decision is shown instead.
    await waitFor(() => expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument());
    expect(await screen.findByText("Looks good.", { selector: "blockquote" })).toBeInTheDocument();
  });

  it("requires a reason before rejecting", async () => {
    const request = makeRequest();
    const db = useFakeBackend(users.manager, { requests: [request] });
    const { user } = renderApp(`/requests/${request.id}`);

    await user.click(await screen.findByRole("button", { name: "Reject" }));
    let dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByRole("button", { name: "Reject request" })).toBeDisabled();
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    await user.type(screen.getByLabelText("Comment"), "Use the shared namespace instead.");
    await user.click(screen.getByRole("button", { name: "Reject" }));
    dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Reject request" }));

    await waitFor(() => expect(db.requests[0]?.status).toBe("REJECTED"));
    expect(await screen.findByText("Use the shared namespace instead.", { selector: "blockquote" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });

  it("explains a decision the backend refuses and keeps the dialog usable", async () => {
    const request = makeRequest();
    useFakeBackend(users.manager, { requests: [request] });
    const { user } = renderApp(`/requests/${request.id}`);
    await screen.findByRole("button", { name: "Approve" });
    // The requester cancels while the page is open; the page still shows it as pending.
    request.status = "CANCELLED";

    await user.click(screen.getByRole("button", { name: "Approve" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Approve request" }));

    expect(await within(dialog).findByText("The request can no longer be changed this way.")).toBeInTheDocument();
  });

  it("never offers the decision to the requester or to auditors", async () => {
    const own = makeRequest({ requesterId: ids.manager, assignedApproverId: ids.admin });
    useFakeBackend(users.manager, { requests: [own] });
    renderApp(`/requests/${own.id}`);
    expect(await screen.findByText(own.requestNumber)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("shows auditors requests read-only", async () => {
    const request = makeRequest();
    useFakeBackend(users.auditor, { requests: [request] });
    renderApp(`/requests/${request.id}`);
    expect(await screen.findByText(request.requestNumber)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel request" })).not.toBeInTheDocument();
  });
});

describe("admin: provisioning retry", () => {
  it("retries a failed job after confirmation", async () => {
    const failed = makeRequest({ status: "PROVISIONING_FAILED" });
    const db = useFakeBackend(users.admin, { requests: [failed] });
    db.provisioning.set(failed.id, {
      status: "FAILED",
      attemptCount: 5,
      nextRunAt: null,
      lastErrorCode: "TIMEOUT",
      lastErrorMessage: "The provider did not finish in time.",
      awaitingProvider: false,
      result: null,
      attempts: [],
    });
    const { user } = renderApp(`/requests/${failed.id}`);

    await user.click(await screen.findByRole("button", { name: "Retry provisioning" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Retry provisioning" }));

    await waitFor(() => expect(db.requests[0]?.status).toBe("PROVISIONING"));
    expect(db.calls.filter((c) => c.path.endsWith("/provisioning/retry"))).toHaveLength(1);
  });
});
