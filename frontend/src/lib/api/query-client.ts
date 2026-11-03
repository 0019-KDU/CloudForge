import { QueryClient } from "@tanstack/react-query";

import { ApiError } from "./problem";

/** Client errors are deterministic; retrying them only delays the error state. */
function shouldRetry(failureCount: number, error: unknown) {
  if (error instanceof ApiError && error.status !== null && error.status < 500) return false;
  return failureCount < 2;
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: shouldRetry,
        refetchOnWindowFocus: true,
      },
      mutations: {
        // Mutations are business operations (approve, submit, retry): never repeat them automatically.
        retry: false,
      },
    },
  });
}
