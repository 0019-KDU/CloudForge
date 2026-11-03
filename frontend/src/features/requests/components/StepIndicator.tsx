import { Check } from "lucide-react";

import { cn } from "@/lib/utils/cn";

interface StepIndicatorProps {
  steps: string[];
  current: number;
  /** Lets users jump back to completed steps. */
  onStepClick?: (index: number) => void;
}

export function StepIndicator({ steps, current, onStepClick }: StepIndicatorProps) {
  return (
    <ol className="flex flex-wrap items-center gap-x-2 gap-y-2 text-sm" aria-label="Progress">
      {steps.map((label, index) => {
        const done = index < current;
        const active = index === current;
        const content = (
          <>
            <span
              className={cn(
                "flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-medium",
                done && "border-primary bg-primary text-primary-foreground",
                active && "border-primary text-primary",
                !done && !active && "text-muted-foreground",
              )}
            >
              {done ? <Check className="size-3.5" aria-hidden /> : index + 1}
            </span>
            <span className={cn(active ? "font-medium" : "text-muted-foreground")}>{label}</span>
          </>
        );
        return (
          <li key={label} className="flex items-center gap-2" aria-current={active ? "step" : undefined}>
            {done && onStepClick ? (
              <button
                type="button"
                onClick={() => onStepClick(index)}
                className="flex items-center gap-2 rounded-md hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              >
                {content}
              </button>
            ) : (
              <span className="flex items-center gap-2">{content}</span>
            )}
            {index < steps.length - 1 ? <span className="mx-1 hidden h-px w-8 bg-border sm:block" aria-hidden /> : null}
          </li>
        );
      })}
    </ol>
  );
}
