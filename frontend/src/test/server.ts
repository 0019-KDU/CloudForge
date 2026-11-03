import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";

import type { UserResponse } from "@/types/api";

import { problem, tokens } from "./fixtures";

/** Absolute path as the app requests it (API base URL "/api" on the jsdom origin). */
export const apiUrl = (path: string) => `/api${path}`;

const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

/** Default: no session. Tests opt in with {@link signedInAs}. */
export const server = setupServer(
  http.post(apiUrl("/auth/refresh"), () =>
    HttpResponse.json(problem(401, "INVALID_REFRESH_TOKEN", "The session has expired. Sign in again."), { status: 401 }),
  ),
  http.post(apiUrl("/auth/logout"), () => new HttpResponse(null, { status: 204 })),
  // The shell polls these on every signed-in page.
  http.get(apiUrl("/notifications/unread-count"), () => HttpResponse.json({ unread: 0 })),
  http.get(apiUrl("/notifications"), () => HttpResponse.json(EMPTY_PAGE)),
  // The overview's counters and lists, which tests landing on "/" render without caring about.
  // Tests that check these lists register their own handlers (server.use takes precedence).
  http.get(apiUrl("/requests"), () => HttpResponse.json(EMPTY_PAGE)),
  http.get(apiUrl("/provisioning-jobs"), () => HttpResponse.json(EMPTY_PAGE)),
  http.get(apiUrl("/users/directory"), () => HttpResponse.json([])),
);

/** Lets the boot-time refresh succeed and /users/me return the given user. */
export function signedInAs(user: UserResponse) {
  server.use(
    http.post(apiUrl("/auth/refresh"), () => HttpResponse.json(tokens())),
    http.get(apiUrl("/users/me"), () => HttpResponse.json(user)),
  );
}
