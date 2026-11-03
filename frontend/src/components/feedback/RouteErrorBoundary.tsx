import { AlertTriangle } from "lucide-react";
import { isRouteErrorResponse, Link, useRouteError } from "react-router";

import { Button } from "@/components/ui/button";

/**
 * Route-level error boundary for render errors and failed lazy chunks. It never shows the raw error:
 * stack traces and internal messages stay in the console.
 */
export function RouteErrorBoundary() {
  const error = useRouteError();
  if (import.meta.env.DEV) console.error(error);

  const notFound = isRouteErrorResponse(error) && error.status === 404;
  const chunkFailed = error instanceof Error && /dynamically imported module|Loading chunk/i.test(error.message);

  return (
    <div className="flex min-h-[60svh] flex-col items-center justify-center px-6 text-center" role="alert">
      <div className="mb-4 flex size-10 items-center justify-center rounded-lg border bg-danger-subtle text-danger">
        <AlertTriangle className="size-5" aria-hidden />
      </div>
      <h1 className="text-base font-semibold">{notFound ? "Page not found" : "This page couldn't be displayed"}</h1>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">
        {chunkFailed
          ? "A new version of CloudForge may have been deployed. Reload the page to continue."
          : "An unexpected error occurred. Reload the page, or go back to the overview."}
      </p>
      <div className="mt-5 flex gap-2">
        <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
          Reload
        </Button>
        <Button size="sm" asChild>
          <Link to="/">Go to overview</Link>
        </Button>
      </div>
    </div>
  );
}
