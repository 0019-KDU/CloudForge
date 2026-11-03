import { Check, X } from "lucide-react";

import { cn } from "@/lib/utils/cn";
import type { RequestResponse } from "@/types/api";

import { lifecycleSteps } from "../lifecycle";

export function RequestLifecycle({ request }: { request: Pick<RequestResponse, "status"> }) {
  const steps = lifecycleSteps(request);
  return (
    <ol className="grid grid-cols-2 gap-3 sm:grid-cols-4" aria-label="Request progress">
      {steps.map((step, index) => (
        <li key={index} className="flex items-center gap-2.5" aria-current={step.state === "current" ? "step" : undefined}>
          <span
            className={cn(
              "flex size-6 shrink-0 items-center justify-center rounded-full border text-xs",
              step.state === "done" && "border-success bg-success text-white dark:text-background",
              step.state === "current" && "border-primary text-primary ring-4 ring-primary/10",
              step.state === "failed" && "border-danger bg-danger text-white dark:text-background",
              (step.state === "upcoming" || step.state === "skipped") && "text-muted-foreground",
            )}
            aria-hidden
          >
            {step.state === "done" ? <Check className="size-3.5" /> : step.state === "failed" ? <X className="size-3.5" /> : index + 1}
          </span>
          <span
            className={cn(
              "text-sm",
              step.state === "current" && "font-medium",
              (step.state === "upcoming" || step.state === "skipped") && "text-muted-foreground",
              step.state === "skipped" && "line-through",
            )}
          >
            {step.label}
            <span className="sr-only">
              {" "}
              ({step.state === "current" ? "in progress" : step.state === "done" ? "complete" : step.state})
            </span>
          </span>
        </li>
      ))}
    </ol>
  );
}
