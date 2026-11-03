import type { UseQueryResult } from "@tanstack/react-query";
import type { LucideIcon } from "lucide-react";
import { Link } from "react-router";

import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils/cn";

interface StatCardProps {
  label: string;
  query: Pick<UseQueryResult<number>, "data" | "isLoading" | "error">;
  icon: LucideIcon;
  to: string;
  tone?: "neutral" | "warning" | "info" | "success" | "danger";
  hint?: string;
}

const ICON_TONE = {
  neutral: "text-muted-foreground",
  warning: "text-warning",
  info: "text-info",
  success: "text-success",
  danger: "text-danger",
};

/** A count that links to the list it summarizes. */
export function StatCard({ label, query, icon: Icon, to, tone = "neutral", hint }: StatCardProps) {
  return (
    <Link
      to={to}
      className="group rounded-lg border bg-card p-4 transition-colors hover:border-primary/40 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
        <Icon className={cn("size-4", ICON_TONE[tone])} aria-hidden />
      </div>
      <div className="mt-2 text-2xl font-semibold tracking-tight tabular-nums">
        {query.isLoading ? (
          <Skeleton className="h-8 w-12" />
        ) : query.error ? (
          <span className="text-sm font-normal text-muted-foreground">Unavailable</span>
        ) : (
          query.data
        )}
      </div>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </Link>
  );
}
