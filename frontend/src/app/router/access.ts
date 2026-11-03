import type { Role } from "@/types/api";

/**
 * Which roles may open which areas. Mirrors the backend rules (RequestAccessPolicy, @PreAuthorize):
 * requests are created by engineers and managers, ASSIGNED lists need MANAGER, ALL lists and audit
 * need PLATFORM_ADMIN or AUDITOR, /api/admin/** needs PLATFORM_ADMIN.
 */
export const ACCESS = {
  requester: ["ENGINEER", "MANAGER"],
  manager: ["MANAGER"],
  oversight: ["PLATFORM_ADMIN", "AUDITOR"],
  admin: ["PLATFORM_ADMIN"],
} as const satisfies Record<string, readonly Role[]>;
