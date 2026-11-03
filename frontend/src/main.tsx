import "./styles/globals.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { z } from "zod";

import { AppProviders } from "./app/providers/AppProviders";
import { createAppRouter } from "./app/router/routes";
import { createQueryClient } from "./lib/api/query-client";

// The production Content-Security-Policy forbids eval; tell Zod not to try its JIT (new Function).
z.config({ jitless: true });

const root = document.getElementById("root");
if (!root) throw new Error("Missing #root element");

const queryClient = createQueryClient();
const router = createAppRouter();

createRoot(root).render(
  <StrictMode>
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
