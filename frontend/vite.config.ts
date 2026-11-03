/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";

import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  // Server-side only (no VITE_ prefix): where the dev server forwards /api.
  const backend = env.CLOUDFORGE_BACKEND_URL || "http://localhost:8080";

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    server: {
      port: 5173,
      strictPort: true,
      // Same origin for the SPA and the API: the backend has no CORS configuration, and the
      // refresh cookie (Path=/api/auth, SameSite=Strict) only works when /api is on this origin.
      proxy: {
        "/api": { target: backend, changeOrigin: false },
      },
    },
    build: {
      sourcemap: true,
      rolldownOptions: {
        output: {
          // Long-lived vendor chunks: app deploys don't invalidate cached libraries. Recharts stays
          // in its own lazy chunk (only the platform overview loads it).
          codeSplitting: {
            groups: [
              { name: "react", test: /node_modules[\\/](react|react-dom|scheduler|react-router)[\\/]/ },
              { name: "data", test: /node_modules[\\/](@tanstack|axios)[\\/]/ },
              { name: "ui", test: /node_modules[\\/](radix-ui|@radix-ui|@floating-ui|sonner)[\\/]/ },
            ],
          },
        },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      css: false,
      restoreMocks: true,
      // Workflow tests render the whole app and click through several steps.
      testTimeout: 20_000,
      include: ["src/**/*.test.{ts,tsx}"],
    },
  };
});
