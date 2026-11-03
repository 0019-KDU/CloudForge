import { api, configureAuth } from "@/lib/api/client";
import { ApiError } from "@/lib/api/problem";
import type { TokenResponse } from "@/types/api";

/**
 * Access-token lifecycle (ADR-0003, docs/frontend/api-integration.md §2).
 *
 * - The access token lives only in this module's memory, never in web storage.
 * - Refresh tokens are single-use and a replayed one revokes the whole session, so at most one
 *   refresh may be in flight: within a tab through a shared promise, across tabs through the Web
 *   Locks API. Once a tab holds the lock, the browser already has the newest cookie.
 */

const REFRESH_LEEWAY_MS = 60_000;
const LOCK_NAME = "cloudforge-refresh";

let accessToken: string | null = null;
let expiresAt = 0;
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let inflight: Promise<string | null> | null = null;

type SessionListener = (event: "expired") => void;
const listeners = new Set<SessionListener>();

export function onSessionEvent(listener: SessionListener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function emitExpired() {
  listeners.forEach((listener) => listener("expired"));
}

export function getAccessToken() {
  return accessToken;
}

export function hasSession() {
  return accessToken !== null;
}

export function storeTokens(tokens: TokenResponse) {
  accessToken = tokens.accessToken;
  const lifetimeMs = tokens.expiresIn * 1000;
  expiresAt = Date.now() + lifetimeMs;

  clearTimeout(refreshTimer);
  // Refresh ahead of expiry; reactive refresh on 401 covers throttled background timers.
  const delay = Math.max(lifetimeMs - REFRESH_LEEWAY_MS, lifetimeMs / 2);
  refreshTimer = setTimeout(() => {
    void refreshAccessToken();
  }, delay);
}

export function clearSession() {
  accessToken = null;
  expiresAt = 0;
  clearTimeout(refreshTimer);
}

function needsRefresh() {
  return accessToken === null || Date.now() >= expiresAt - 5_000;
}

async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && "locks" in navigator) {
    return navigator.locks.request(LOCK_NAME, task);
  }
  return task();
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request.
 * Resolves to null (and clears the session) when the refresh token is no longer valid; rejects
 * only for other failures such as a network error, which leave the session as it was.
 */
export function refreshAccessToken(): Promise<string | null> {
  inflight ??= withRefreshLock(async () => {
    const { data } = await api.post<TokenResponse>("/auth/refresh");
    storeTokens(data);
    return data.accessToken;
  })
    .catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 401) {
        clearSession();
        return null;
      }
      throw error;
    })
    .finally(() => {
      inflight = null;
    });
  return inflight;
}

configureAuth({
  getAccessToken,
  needsRefresh,
  refresh: async () => {
    try {
      return await refreshAccessToken();
    } catch {
      // A transient failure: let the original request fail normally instead of signing out.
      return accessToken;
    }
  },
  onSessionExpired: () => {
    clearSession();
    emitExpired();
  },
});

/** Test-only: reset module state between tests. */
export function resetSessionForTests() {
  clearSession();
  inflight = null;
  listeners.clear();
}
