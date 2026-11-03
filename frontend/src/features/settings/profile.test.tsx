import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { problem, users } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { apiUrl, server, signedInAs } from "@/test/server";

function withDirectory() {
  server.use(http.get(apiUrl("/users/directory"), () => HttpResponse.json([{ ...users.manager, status: "ACTIVE" }])));
}

describe("profile", () => {
  it("shows the manager who approves the user's requests", async () => {
    signedInAs(users.engineer);
    withDirectory();
    renderApp("/profile");

    expect(await screen.findByText("Max Manager")).toBeInTheDocument();
  });

  it("puts a wrong current password on the right field", async () => {
    signedInAs(users.engineer);
    withDirectory();
    server.use(
      http.post(apiUrl("/users/me/password"), () =>
        HttpResponse.json(problem(422, "INVALID_CURRENT_PASSWORD", "The current password is incorrect."), {
          status: 422,
        }),
      ),
    );
    const { user } = renderApp("/profile");

    const form = (await screen.findByRole("button", { name: "Change password" })).closest("form");
    if (!form) throw new Error("password form not found");
    await user.type(within(form).getByLabelText("Current password"), "not my password");
    await user.type(within(form).getByLabelText("New password"), "a much longer passphrase");
    await user.type(within(form).getByLabelText("Confirm new password"), "a much longer passphrase");
    await user.click(within(form).getByRole("button", { name: "Change password" }));

    expect(await within(form).findByText("The current password is incorrect.")).toBeInTheDocument();
    expect(within(form).getByLabelText("Current password")).toHaveAttribute("aria-invalid", "true");
  });

  it("signs the user out after a password change, as the backend revoked every session", async () => {
    signedInAs(users.engineer);
    withDirectory();
    server.use(http.post(apiUrl("/users/me/password"), () => new HttpResponse(null, { status: 204 })));
    const { user, router } = renderApp("/profile");

    const form = (await screen.findByRole("button", { name: "Change password" })).closest("form");
    if (!form) throw new Error("password form not found");
    await user.type(within(form).getByLabelText("Current password"), "old passphrase here");
    await user.type(within(form).getByLabelText("New password"), "a much longer passphrase");
    await user.type(within(form).getByLabelText("Confirm new password"), "a much longer passphrase");
    await user.click(within(form).getByRole("button", { name: "Change password" }));

    expect(await screen.findByText("Your password was changed. Sign in with the new password.")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it("checks the new password length before calling the backend", async () => {
    signedInAs(users.engineer);
    withDirectory();
    const { user } = renderApp("/profile");

    const form = (await screen.findByRole("button", { name: "Change password" })).closest("form");
    if (!form) throw new Error("password form not found");
    await user.type(within(form).getByLabelText("Current password"), "old passphrase here");
    await user.type(within(form).getByLabelText("New password"), "short");
    await user.type(within(form).getByLabelText("Confirm new password"), "different");
    await user.click(within(form).getByRole("button", { name: "Change password" }));

    expect(await within(form).findByText("Use at least 12 characters.")).toBeInTheDocument();
    expect(within(form).getByText("Passwords don't match.")).toBeInTheDocument();
  });
});
