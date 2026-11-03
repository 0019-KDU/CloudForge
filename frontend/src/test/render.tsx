import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";

import { AppProviders } from "@/app/providers/AppProviders";
import { routes } from "@/app/router/routes";
import { createQueryClient } from "@/lib/api/query-client";

/** Renders the real route tree at `path`, with fresh providers and a fresh query cache. */
export function renderApp(path = "/") {
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({
    queries: { ...queryClient.getDefaultOptions().queries, retry: false },
  });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const user = userEvent.setup();
  const utils = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...utils, user, router, queryClient };
}
