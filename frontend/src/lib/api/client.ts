import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";

import { toApiError } from "./problem";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

/**
 * The single HTTP client. It knows nothing about how tokens are obtained: the auth feature plugs
 * that in through {@link configureAuth}, which keeps the dependency one-way (auth → client).
 */
export const api = axios.create({
  baseURL: API_BASE_URL,
  // Required so the browser stores and sends the HttpOnly refresh cookie on /api/auth calls.
  withCredentials: true,
  headers: { Accept: "application/json, application/problem+json" },
  // Spring binds repeated params (?status=A&status=B) to a Set; Axios' default would send status[]=A.
  paramsSerializer: { indexes: null },
  timeout: 30_000,
});

interface AuthHooks {
  getAccessToken: () => string | null;
  /** True when the token is missing or about to expire. */
  needsRefresh: () => boolean;
  /** Resolves to a fresh token, or null when the session is gone. */
  refresh: () => Promise<string | null>;
  onSessionExpired: () => void;
}

let auth: AuthHooks | null = null;

export function configureAuth(hooks: AuthHooks) {
  auth = hooks;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

/** Token endpoints authenticate with the cookie only and must never trigger a refresh themselves. */
function isAuthEndpoint(url: string | undefined) {
  return url?.startsWith("/auth/") ?? false;
}

api.interceptors.request.use(async (config) => {
  config.headers.set("X-Correlation-Id", crypto.randomUUID());
  if (!auth || isAuthEndpoint(config.url)) return config;

  let token = auth.getAccessToken();
  if (token && auth.needsRefresh()) {
    token = await auth.refresh();
  }
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    const unauthenticated = error.response?.status === 401;

    if (auth && config && unauthenticated && !isAuthEndpoint(config.url) && !config._retried) {
      config._retried = true;
      const token = await auth.refresh();
      if (token) {
        config.headers.set("Authorization", `Bearer ${token}`);
        return api.request(config);
      }
      auth.onSessionExpired();
    }
    return Promise.reject(toApiError(error));
  },
);
