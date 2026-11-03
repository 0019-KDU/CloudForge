import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

import type { UserResponse } from "@/types/api";

import { problem, tokens } from "./fixtures";

/** Absolute path as the app requests it (API base URL "/api" on the jsdom origin). */
export const apiUrl = (path: string) => `/api${path}`;

/** Default: no session. Tests opt in with {@link signedInAs}. */
export const server = setupServer(
  http.post(apiUrl("/auth/refresh"), () =>
    HttpResponse.json(problem(401, "INVALID_REFRESH_TOKEN", "The session has expired. Sign in again."), { status: 401 }),
  ),
  http.post(apiUrl("/auth/logout"), () => new HttpResponse(null, { status: 204 })),
  // The shell polls these on every signed-in page.
  http.get(apiUrl("/notifications/unread-count"), () => HttpResponse.json({ unread: 0 })),
  http.get(apiUrl("/notifications"), () => HttpResponse.json({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })),
);

/** Lets the boot-time refresh succeed and /users/me return the given user. */
export function signedInAs(user: UserResponse) {
  server.use(
    http.post(apiUrl("/auth/refresh"), () => HttpResponse.json(tokens())),
    http.get(apiUrl("/users/me"), () => HttpResponse.json(user)),
  );
}
