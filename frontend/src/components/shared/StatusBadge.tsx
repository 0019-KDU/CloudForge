import {
  AlertTriangle,
  Ban,
  CheckCircle,
  CircleCheckBig,
  Clock,
  FilePen,
  Hourglass,
  Loader,
  XCircle,
  type LucideIcon,
} from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { environmentLabel, humanize, statusLabel } from "@/lib/utils/labels";
import type { Environment, JobStatus, RequestStatus } from "@/types/api";

export type Tone = "neutral" | "warning" | "info" | "success" | "danger";

const TONE_CLASSES: Record<Tone, string> = {
  neutral: "bg-neutral-subtle text-neutral border-neutral/20",
  warning: "bg-warning-subtle text-warning border-warning/25",
  info: "bg-info-subtle text-info border-info/25",
  success: "bg-success-subtle text-success border-success/25",
  danger: "bg-danger-subtle text-danger border-danger/25",
};

interface ToneBadgeProps {
  tone: Tone;
  icon?: LucideIcon;
  label: string;
  spin?: boolean;
  className?: string;
}

/** Status pill: always icon + text, never colour alone. */
export function ToneBadge({ tone, icon: Icon, label, spin, className }: ToneBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex h-6 shrink-0 items-center gap-1.5 rounded-full border px-2 text-xs font-medium whitespace-nowrap",
        TONE_CLASSES[tone],
        className,
      )}
    >
      {Icon ? <Icon className={cn("size-3.5", spin && "animate-spin [animation-duration:2s]")} aria-hidden /> : null}
      {label}
    </span>
  );
}

const REQUEST_STATUS: Record<RequestStatus, { tone: Tone; icon: LucideIcon; spin?: boolean }> = {
  DRAFT: { tone: "neutral", icon: FilePen },
  PENDING_APPROVAL: { tone: "warning", icon: Clock },
  APPROVED: { tone: "info", icon: CheckCircle },
  PROVISIONING: { tone: "info", icon: Loader, spin: true },
  PROVISIONED: { tone: "success", icon: CircleCheckBig },
  REJECTED: { tone: "danger", icon: XCircle },
  PROVISIONING_FAILED: { tone: "danger", icon: AlertTriangle },
  CANCELLED: { tone: "neutral", icon: Ban },
};

export function RequestStatusBadge({ status, className }: { status: RequestStatus; className?: string }) {
  const style = REQUEST_STATUS[status];
  return (
    <ToneBadge
      tone={style.tone}
      icon={style.icon}
      spin={style.spin ?? false}
      label={statusLabel(status)}
      className={cn(status === "CANCELLED" && "opacity-75", className)}
    />
  );
}

const JOB_STATUS: Record<JobStatus, { tone: Tone; icon: LucideIcon; spin?: boolean; label: string }> = {
  QUEUED: { tone: "neutral", icon: Hourglass, label: "Queued" },
  RUNNING: { tone: "info", icon: Loader, spin: true, label: "Running" },
  AWAITING_PROVIDER: { tone: "info", icon: Clock, label: "Awaiting provider" },
  SUCCEEDED: { tone: "success", icon: CircleCheckBig, label: "Succeeded" },
  FAILED: { tone: "danger", icon: AlertTriangle, label: "Failed" },
};

export function JobStatusBadge({ status }: { status: JobStatus }) {
  const style = JOB_STATUS[status];
  return <ToneBadge tone={style.tone} icon={style.icon} spin={style.spin ?? false} label={style.label} />;
}

/** Production stands out so it isn't approved by accident. */
export function EnvironmentBadge({ environment }: { environment: Environment }) {
  return (
    <span
      className={cn(
        "inline-flex h-6 items-center rounded-md border px-2 text-xs font-medium whitespace-nowrap",
        environment === "PRODUCTION" ? "border-warning/40 text-warning" : "text-muted-foreground",
      )}
    >
      {environmentLabel(environment)}
    </span>
  );
}

export function CodeBadge({ value }: { value: string }) {
  return <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{humanize(value)}</code>;
}
