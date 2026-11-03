import { AlertTriangle, Lock, RotateCw, SearchX, WifiOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { toApiError } from "@/lib/api/problem";
import { cn } from "@/lib/utils/cn";

interface ErrorStateProps {
  error: unknown;
  onRetry?: () => void;
  /** Overrides the default heading. */
  title?: string;
  className?: string;
}

/** Inline error for a failed query: friendly text, a retry action, and the correlation ID for support. */
export function ErrorState({ error, onRetry, title, className }: ErrorStateProps) {
  const apiError = toApiError(error);
  const { icon: Icon, heading } = presentation(apiError.kind);
  const retryable = apiError.kind === "network" || apiError.kind === "server" || apiError.kind === "unknown";

  return (
    <div role="alert" className={cn("flex flex-col items-center justify-center px-6 py-14 text-center", className)}>
      <div className="mb-4 flex size-10 items-center justify-center rounded-lg border bg-danger-subtle text-danger">
        <Icon className="size-5" aria-hidden />
      </div>
      <h3 className="text-sm font-semibold">{title ?? heading}</h3>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">{apiError.message}</p>
      {apiError.correlationId ? (
        <p className="mt-2 text-xs text-muted-foreground">
          Reference: <code className="select-all">{apiError.correlationId}</code>
        </p>
      ) : null}
      {onRetry && retryable ? (
        <Button variant="outline" size="sm" className="mt-5" onClick={onRetry}>
          <RotateCw /> Try again
        </Button>
      ) : null}
    </div>
  );
}

function presentation(kind: ReturnType<typeof toApiError>["kind"]) {
  switch (kind) {
    case "not-found":
      return { icon: SearchX, heading: "Not found" };
    case "forbidden":
      return { icon: Lock, heading: "Access denied" };
    case "network":
      return { icon: WifiOff, heading: "Connection problem" };
    default:
      return { icon: AlertTriangle, heading: "Something went wrong" };
  }
}
