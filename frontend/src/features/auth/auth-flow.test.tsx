import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { problem, tokens, users } from "@/test/fixtures";
import { renderApp } from "@/test/render";
import { apiUrl, server, signedInAs } from "@/test/server";

describe("authentication flow", () => {
  it("sends anonymous visitors to sign-in and back to the page they wanted", async () => {
    let loginBody: unknown;
    server.use(
      http.post(apiUrl("/auth/login"), async ({ request }) => {
        loginBody = await request.json();
        return HttpResponse.json(tokens());
      }),
      http.get(apiUrl("/users/me"), () => HttpResponse.json(users.engineer)),
    );
    const { user, router } = renderApp("/profile");

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(router.state.location.search).toBe("?next=%2Fprofile");

    await user.type(screen.getByLabelText("Email"), "erin.engineer@acme.com");
    await user.type(screen.getByLabelText("Password"), "correct horse battery");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("heading", { name: "Profile" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/profile");
    expect(loginBody).toEqual({ email: "erin.engineer@acme.com", password: "correct horse battery" });
  });

  it("validates the form before calling the backend", async () => {
    const { user } = renderApp("/login");

    await user.click(await screen.findByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Enter your email address.")).toBeInTheDocument();
    expect(screen.getByText("Enter your password.")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
  });

  it("shows a generic message for wrong credentials and clears the password", async () => {
    server.use(
      http.post(apiUrl("/auth/login"), () =>
        HttpResponse.json(problem(401, "INVALID_CREDENTIALS", "Email or password is incorrect."), { status: 401 }),
      ),
    );
    const { user } = renderApp("/login");

    await user.type(await screen.findByLabelText("Email"), "erin.engineer@acme.com");
    await user.type(screen.getByLabelText("Password"), "wrong password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Email or password is incorrect.");
    expect(screen.getByLabelText("Password")).toHaveValue("");
  });

  it("explains rate limiting with the wait time", async () => {
    server.use(
      http.post(apiUrl("/auth/login"), () =>
        HttpResponse.json(problem(429, "TOO_MANY_ATTEMPTS", "Too many attempts."), {
          status: 429,
          headers: { "Retry-After": "180" },
        }),
      ),
    );
    const { user } = renderApp("/login");

    await user.type(await screen.findByLabelText("Email"), "erin.engineer@acme.com");
    await user.type(screen.getByLabelText("Password"), "whatever");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Try again in 3 minutes.");
  });

  it("resumes an existing session from the refresh cookie on page load", async () => {
    signedInAs(users.manager);
    renderApp("/");

    expect(await screen.findByRole("heading", { name: "Overview" })).toBeInTheDocument();
    expect(screen.getByText("Welcome back, Max.")).toBeInTheDocument();
  });

  it("signs out and returns to the sign-in page", async () => {
    signedInAs(users.engineer);
    let loggedOut = false;
    server.use(
      http.post(apiUrl("/auth/logout"), () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp("/");

    await user.click(await screen.findByRole("button", { name: /account menu/i }));
    await user.click(await screen.findByRole("menuitem", { name: "Sign out" }));

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(loggedOut).toBe(true);
  });

  it("tells the user when the session expired", async () => {
    signedInAs(users.engineer);
    const { router } = renderApp("/");
    await screen.findByRole("heading", { name: "Overview" });

    // The refresh token has been revoked meanwhile: the profile page's directory call gets a 401 and
    // the session cannot be renewed.
    server.use(
      http.post(apiUrl("/auth/refresh"), () =>
        HttpResponse.json(problem(401, "INVALID_REFRESH_TOKEN", "The session has expired."), { status: 401 }),
      ),
      http.get(apiUrl("/users/directory"), () =>
        HttpResponse.json(problem(401, "AUTHENTICATION_REQUIRED", "Authentication is required."), { status: 401 }),
      ),
    );
    await router.navigate("/profile");

    expect(await screen.findByText("Your session has expired. Sign in again to continue.")).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toContain("reason=expired"));
  });
});
