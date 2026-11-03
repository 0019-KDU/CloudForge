import type { UserResponse } from "@/types/api";

import { authApi } from "./api";
import { clearSession, refreshAccessToken } from "./session";

/**
 * Resumes the session after a page load: the access token was only in memory, so try the refresh
 * cookie once. Module-level so React StrictMode's double effects cannot send two refreshes (which
 * the backend would treat as token reuse and revoke the session).
 */
let bootPromise: Promise<UserResponse | null> | null = null;

export function resumeSession(): Promise<UserResponse | null> {
  bootPromise ??= (async () => {
    try {
      const token = await refreshAccessToken();
      return token ? await authApi.me() : null;
    } catch {
      clearSession();
      return null;
    }
  })();
  return bootPromise;
}

/** After an explicit sign-in or sign-out, later mounts must not resume a stale result. */
export function settleBoot(user: UserResponse | null) {
  bootPromise = Promise.resolve(user);
}

export function resetBootForTests() {
  bootPromise = null;
}
