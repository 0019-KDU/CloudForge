import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import { AppProviders } from "@/app/providers/AppProviders";
import { createQueryClient } from "@/lib/api/query-client";
import { safeNext } from "@/lib/utils/navigation";
import { users } from "@/test/fixtures";
import { signedInAs } from "@/test/server";

import { RequireAuth, RequireRole } from "./guards";
import { navigationFor } from "./navigation";

function renderGuarded() {
  const router = createMemoryRouter(
    [
      {
        element: <RequireAuth />,
        children: [
          {
            path: "/admin-only",
            element: (
              <RequireRole roles={["PLATFORM_ADMIN"]}>
                <h1>Admin area</h1>
              </RequireRole>
            ),
          },
        ],
      },
      { path: "/login", element: <h1>Login</h1> },
    ],
    { initialEntries: ["/admin-only"] },
  );
  render(
    <AppProviders queryClient={createQueryClient()}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
}

describe("route protection", () => {
  it("shows the unauthorized page to users without the required role", async () => {
    signedInAs(users.engineer);
    renderGuarded();

    expect(await screen.findByText("You don't have access to this page")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Admin area" })).not.toBeInTheDocument();
  });

  it("renders the page for users with the role", async () => {
    signedInAs(users.admin);
    renderGuarded();

    expect(await screen.findByRole("heading", { name: "Admin area" })).toBeInTheDocument();
  });

  it("redirects anonymous users to sign-in", async () => {
    renderGuarded();

    expect(await screen.findByRole("heading", { name: "Login" })).toBeInTheDocument();
  });

  it("only follows same-app paths after sign-in", () => {
    expect(safeNext(new URLSearchParams("next=/requests/1"))).toBe("/requests/1");
    expect(safeNext(new URLSearchParams("next=//evil.example"))).toBe("/");
    expect(safeNext(new URLSearchParams("next=https://evil.example"))).toBe("/");
    expect(safeNext(new URLSearchParams("next=/\\evil.example"))).toBe("/");
  });
});

describe("navigation", () => {
  it("shows every signed-in user the common items", () => {
    for (const user of Object.values(users)) {
      const labels = navigationFor(user).flatMap((section) => section.items.map((item) => item.label));
      expect(labels).toEqual(expect.arrayContaining(["Overview", "Profile"]));
    }
  });
});
