import { Cloud } from "lucide-react";
import { Suspense } from "react";
import { Outlet } from "react-router";

import { FullPageLoader } from "@/components/feedback/Loaders";

/** Centered card layout for sign-in, registration and password reset. */
export function AuthLayout() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center bg-muted/40 px-4 py-10">
      <div className="mb-8 flex items-center gap-2">
        <div className="flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground">
          <Cloud className="size-4" aria-hidden />
        </div>
        <span className="text-base font-semibold tracking-tight">CloudForge</span>
      </div>
      <main className="w-full max-w-sm">
        <Suspense fallback={<FullPageLoader />}>
          <Outlet />
        </Suspense>
      </main>
      <p className="mt-8 text-xs text-muted-foreground">Cloud resource provisioning and governance</p>
    </div>
  );
}
