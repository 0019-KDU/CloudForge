import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { useFakeBackend } from "@/test/fake-backend";
import { users } from "@/test/fixtures";
import { renderApp } from "@/test/render";

import { navigationFor } from "./navigation";

const labels = (user: (typeof users)[keyof typeof users]) =>
  navigationFor(user).flatMap((section) => section.items.map((item) => item.label));

describe("role-based navigation", () => {
  it("gives engineers request pages but no approvals, operations or governance", () => {
    const nav = labels(users.engineer);
    expect(nav).toEqual(expect.arrayContaining(["My requests", "New request", "Resource catalog", "Resource inventory"]));
    expect(nav).not.toEqual(expect.arrayContaining(["Pending approvals"]));
    expect(nav).not.toContain("Audit logs");
    expect(nav).not.toContain("Users");
  });

  it("gives managers approvals as well as their own requests", () => {
    expect(labels(users.manager)).toEqual(expect.arrayContaining(["Pending approvals", "Approval history", "My requests"]));
  });

  it("gives admins governance but not request creation", () => {
    const nav = labels(users.admin);
    expect(nav).toEqual(expect.arrayContaining(["All requests", "Provisioning jobs", "Audit logs", "Users", "Resource policies"]));
    expect(nav).not.toContain("New request");
  });

  it("gives auditors read-only oversight only", () => {
    const nav = labels(users.auditor);
    expect(nav).toEqual(expect.arrayContaining(["All requests", "Provisioning jobs", "Audit logs"]));
    expect(nav).not.toContain("Users");
    expect(nav).not.toContain("New request");
  });

  it("renders the sidebar for the signed-in role", async () => {
    useFakeBackend(users.engineer);
    renderApp("/catalog");
    const nav = await screen.findByRole("navigation", { name: "Main" });
    expect(within(nav).getByRole("link", { name: "My requests" })).toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "Users" })).not.toBeInTheDocument();
  });
});

describe("restricted routes", () => {
  it.each([
    ["engineer", users.engineer, "/admin/users"],
    ["engineer", users.engineer, "/approvals"],
    ["engineer", users.engineer, "/audit"],
    ["auditor", users.auditor, "/admin/catalog"],
    ["auditor", users.auditor, "/requests/new"],
    ["admin", users.admin, "/requests/new"],
  ])("blocks the %s from %s", async (_, user, path) => {
    useFakeBackend(user);
    renderApp(path);
    expect(await screen.findByText("You don't have access to this page")).toBeInTheDocument();
  });
});
