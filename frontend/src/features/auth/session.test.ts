import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";

import { api } from "@/lib/api/client";
import { ApiError } from "@/lib/api/problem";
import { problem, tokens } from "@/test/fixtures";
import { apiUrl, server } from "@/test/server";

import { getAccessToken, onSessionEvent, refreshAccessToken, storeTokens } from "./session";

describe("session", () => {
  it("sends only one refresh request for concurrent callers", async () => {
    let refreshes = 0;
    server.use(
      http.post(apiUrl("/auth/refresh"), () => {
        refreshes++;
        return HttpResponse.json(tokens("fresh-token"));
      }),
    );

    const results = await Promise.all([refreshAccessToken(), refreshAccessToken(), refreshAccessToken()]);

    expect(refreshes).toBe(1);
    expect(results).toEqual(["fresh-token", "fresh-token", "fresh-token"]);
    expect(getAccessToken()).toBe("fresh-token");
  });

  it("adds the bearer token and a correlation ID to API calls", async () => {
    storeTokens(tokens("token-abc"));
    let authorization: string | null = null;
    let correlationId: string | null = null;
    server.use(
      http.get(apiUrl("/users/me"), ({ request }) => {
        authorization = request.headers.get("Authorization");
        correlationId = request.headers.get("X-Correlation-Id");
        return HttpResponse.json({});
      }),
    );

    await api.get("/users/me");

    expect(authorization).toBe("Bearer token-abc");
    expect(correlationId).toMatch(/^[A-Za-z0-9._-]{8,64}$/);
  });

  it("refreshes once on 401 and replays the request with the new token", async () => {
    storeTokens(tokens("expired-token"));
    let refreshes = 0;
    server.use(
      http.post(apiUrl("/auth/refresh"), () => {
        refreshes++;
        return HttpResponse.json(tokens("renewed-token"));
      }),
      http.get(apiUrl("/requests"), ({ request }) =>
        request.headers.get("Authorization") === "Bearer renewed-token"
          ? HttpResponse.json({ ok: true })
          : HttpResponse.json(problem(401, "AUTHENTICATION_REQUIRED", "Authentication is required."), { status: 401 }),
      ),
    );

    const response = await api.get<{ ok: boolean }>("/requests");

    expect(response.data.ok).toBe(true);
    expect(refreshes).toBe(1);
  });

  it("ends the session when the refresh token is no longer valid", async () => {
    storeTokens(tokens("expired-token"));
    const listener = vi.fn();
    onSessionEvent(listener);
    server.use(
      http.get(apiUrl("/requests"), () =>
        HttpResponse.json(problem(401, "AUTHENTICATION_REQUIRED", "Authentication is required."), { status: 401 }),
      ),
    );

    await expect(api.get("/requests")).rejects.toBeInstanceOf(ApiError);

    expect(listener).toHaveBeenCalledWith("expired");
    expect(getAccessToken()).toBeNull();
  });

  it("never retries the token endpoints themselves", async () => {
    let refreshes = 0;
    server.use(
      http.post(apiUrl("/auth/refresh"), () => {
        refreshes++;
        return HttpResponse.json(problem(401, "INVALID_REFRESH_TOKEN", "The session has expired."), { status: 401 });
      }),
      http.post(apiUrl("/auth/login"), () =>
        HttpResponse.json(problem(401, "INVALID_CREDENTIALS", "Email or password is incorrect."), { status: 401 }),
      ),
    );

    await expect(api.post("/auth/login", {})).rejects.toMatchObject({ code: "INVALID_CREDENTIALS" });
    expect(refreshes).toBe(0);
  });
});
