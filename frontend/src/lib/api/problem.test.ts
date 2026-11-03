import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { problem } from "@/test/fixtures";
import { apiUrl, server } from "@/test/server";

import { api } from "./client";
import { ApiError } from "./problem";

async function failingCall(): Promise<ApiError> {
  try {
    await api.get("/probe");
  } catch (error) {
    if (error instanceof ApiError) return error;
    throw error;
  }
  throw new Error("expected the call to fail");
}

describe("API error normalization", () => {
  it("maps a problem body with field errors to a validation error", async () => {
    server.use(
      http.get(apiUrl("/probe"), () =>
        HttpResponse.json(
          problem(422, "INVALID_RESOURCE_SPEC", "The resource specification is invalid.", {
            errors: [{ field: "spec.namespace", message: "is reserved by Kubernetes" }],
          }),
          { status: 422, headers: { "Content-Type": "application/problem+json" } },
        ),
      ),
    );

    const error = await failingCall();
    expect(error.kind).toBe("validation");
    expect(error.code).toBe("INVALID_RESOURCE_SPEC");
    expect(error.fieldErrors).toEqual([{ field: "spec.namespace", message: "is reserved by Kubernetes" }]);
    expect(error.correlationId).toBe("test-correlation-id");
  });

  it("shows the backend's business-rule detail", async () => {
    server.use(
      http.get(apiUrl("/probe"), () =>
        HttpResponse.json(problem(422, "QUOTA_EXCEEDED", "You already have 2 active requests; the limit is 2."), {
          status: 422,
        }),
      ),
    );

    const error = await failingCall();
    expect(error.kind).toBe("business");
    expect(error.message).toBe("You already have 2 active requests; the limit is 2.");
  });

  it("never shows server error details", async () => {
    server.use(
      http.get(apiUrl("/probe"), () =>
        HttpResponse.json(problem(500, "INTERNAL_ERROR", "NullPointerException at com.cloudforge..."), { status: 500 }),
      ),
    );

    const error = await failingCall();
    expect(error.kind).toBe("server");
    expect(error.message).not.toContain("NullPointerException");
  });

  it("reads Retry-After on 429", async () => {
    server.use(
      http.get(apiUrl("/probe"), () =>
        HttpResponse.json(problem(429, "TOO_MANY_ATTEMPTS", "Too many attempts."), {
          status: 429,
          headers: { "Retry-After": "120" },
        }),
      ),
    );

    const error = await failingCall();
    expect(error.kind).toBe("rate-limited");
    expect(error.retryAfter).toBe(120);
  });

  it("handles responses that are not problem documents", async () => {
    server.use(http.get(apiUrl("/probe"), () => new HttpResponse("<html>Bad gateway</html>", { status: 502 })));

    const error = await failingCall();
    expect(error.kind).toBe("server");
    expect(error.code).toBe("HTTP_502");
  });

  it("reports network failures", async () => {
    server.use(http.get(apiUrl("/probe"), () => HttpResponse.error()));

    const error = await failingCall();
    expect(error.kind).toBe("network");
    expect(error.status).toBeNull();
  });
});
