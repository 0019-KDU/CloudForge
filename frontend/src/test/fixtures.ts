import type { ProblemDetail, Role, TokenResponse, UserResponse } from "@/types/api";

/** Backend-shaped test data. Values follow the real DTOs in src/types/api.ts. */

export const ids = {
  engineer: "0192f0a0-0000-7000-8000-000000000001",
  manager: "0192f0a0-0000-7000-8000-000000000002",
  admin: "0192f0a0-0000-7000-8000-000000000003",
  auditor: "0192f0a0-0000-7000-8000-000000000004",
  team: "0192f0a0-0000-7000-8000-0000000000aa",
} as const;

export function makeUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: ids.engineer,
    email: "erin.engineer@acme.com",
    displayName: "Erin Engineer",
    status: "ACTIVE",
    roles: ["ENGINEER"],
    managerId: ids.manager,
    teamId: ids.team,
    createdAt: "2026-01-15T09:00:00Z",
    updatedAt: "2026-01-15T09:00:00Z",
    version: 0,
    ...overrides,
  };
}

export const users: Record<"engineer" | "manager" | "admin" | "auditor", UserResponse> = {
  engineer: makeUser(),
  manager: makeUser({
    id: ids.manager,
    email: "max.manager@acme.com",
    displayName: "Max Manager",
    roles: ["MANAGER"],
    managerId: null,
  }),
  admin: makeUser({
    id: ids.admin,
    email: "ada.admin@acme.com",
    displayName: "Ada Admin",
    roles: ["PLATFORM_ADMIN"],
    managerId: null,
    teamId: null,
  }),
  auditor: makeUser({
    id: ids.auditor,
    email: "aria.auditor@acme.com",
    displayName: "Aria Auditor",
    roles: ["AUDITOR"],
    managerId: null,
    teamId: null,
  }),
};

export function withRoles(user: UserResponse, roles: Role[]): UserResponse {
  return { ...user, roles };
}

export function tokens(accessToken = "access-token-1", expiresIn = 600): TokenResponse {
  return { accessToken, tokenType: "Bearer", expiresIn };
}

export function problem(status: number, code: string, detail: string, extra: Partial<ProblemDetail> = {}): ProblemDetail {
  return {
    type: "about:blank",
    title: "Error",
    status,
    detail,
    code,
    correlationId: "test-correlation-id",
    timestamp: "2026-10-30T12:00:00Z",
    ...extra,
  };
}
