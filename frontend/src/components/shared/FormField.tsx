import { useId, type ReactElement, type ReactNode } from "react";
import { cloneElement } from "react";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";

interface FormFieldProps {
  label: ReactNode;
  /** The control; receives id, aria-invalid and aria-describedby. */
  children: ReactElement<{ id?: string; "aria-invalid"?: boolean; "aria-describedby"?: string }>;
  error?: string | undefined;
  hint?: ReactNode;
  className?: string;
  /** Rendered after the label, e.g. a "Forgot password?" link. */
  labelAside?: ReactNode;
  optional?: boolean;
}

/** Label, control, hint and error wired together for screen readers. */
export function FormField({ label, children, error, hint, className, labelAside, optional }: FormFieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className={cn("space-y-1.5", className)}>
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={id}>
          {label}
          {optional ? <span className="font-normal text-muted-foreground"> (optional)</span> : null}
        </Label>
        {labelAside}
      </div>
      {cloneElement(children, { id, "aria-invalid": error ? true : undefined, "aria-describedby": describedBy })}
      {hint && !error ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-xs font-medium text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
