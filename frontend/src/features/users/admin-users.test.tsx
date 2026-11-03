import { screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { useFakeBackend } from "@/test/fake-backend";
import { problem, users } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { apiUrl, server } from "@/test/server";
import type { PageResponse, UserResponse } from "@/types/api";

const pageOf = <T,>(content: T[]): PageResponse<T> => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });

function adminHandlers(target: UserResponse) {
  const seen: { ifMatch: string | null; body: unknown }[] = [];
  let current = target;
  server.use(
    http.get(apiUrl("/admin/users"), () => HttpResponse.json(pageOf(Object.values(users)))),
    http.get(apiUrl("/admin/teams"), () => HttpResponse.json(pageOf([]))),
    http.get(apiUrl("/admin/departments"), () => HttpResponse.json(pageOf([]))),
    http.get(apiUrl("/admin/users/:id"), () => HttpResponse.json(current, { headers: { ETag: `"${current.version}"` } })),
    http.put(apiUrl("/admin/users/:id/roles"), async ({ request }) => {
      const body = (await request.json()) as { roles: UserResponse["roles"] };
      seen.push({ ifMatch: request.headers.get("If-Match"), body });
      if (request.headers.get("If-Match") !== `"${current.version}"`) {
        return HttpResponse.json(problem(412, "PRECONDITION_FAILED", "The user was changed by someone else."), { status: 412 });
      }
      current = { ...current, roles: body.roles, version: current.version + 1 };
      return HttpResponse.json(current);
    }),
  );
  return {
    seen,
    changeBehindTheScenes: () => {
      current = { ...current, version: current.version + 1 };
    },
  };
}

describe("admin: user management", () => {
  it("lists users with their roles", async () => {
    useFakeBackend(users.admin);
    adminHandlers(users.engineer);
    renderApp("/admin/users");

    const table = await screen.findByRole("table", { name: "Users" });
    expect(within(table).getByRole("link", { name: /Erin Engineer/ })).toHaveAttribute("href", `/admin/users/${users.engineer.id}`);
    expect(within(table).getAllByText("Platform admin").length).toBeGreaterThan(0);
  });

  it("changes roles with the loaded version as If-Match", async () => {
    useFakeBackend(users.admin);
    const api = adminHandlers(users.engineer);
    const { user } = renderApp(`/admin/users/${users.engineer.id}`);

    await user.click(await screen.findByRole("checkbox", { name: "Manager" }));
    await user.click(screen.getByRole("button", { name: "Save roles" }));

    await waitFor(() => expect(api.seen).toHaveLength(1));
    expect(api.seen[0]).toEqual({ ifMatch: '"0"', body: { roles: ["ENGINEER", "MANAGER"] } });
    expect(await screen.findByText("Roles updated")).toBeInTheDocument();
  });

  it("reports a concurrent change instead of overwriting it", async () => {
    useFakeBackend(users.admin);
    const api = adminHandlers(users.engineer);
    const { user } = renderApp(`/admin/users/${users.engineer.id}`);

    await user.click(await screen.findByRole("checkbox", { name: "Auditor" }));
    api.changeBehindTheScenes();
    await user.click(screen.getByRole("button", { name: "Save roles" }));

    expect(await screen.findByText("The user was changed by someone else.")).toBeInTheDocument();
  });
});
