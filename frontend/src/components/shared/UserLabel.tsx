import { Bot } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/features/auth/auth-context";
import { cn } from "@/lib/utils/cn";
import type { DirectoryEntry, UUID } from "@/types/api";

interface UserLabelProps {
  id: UUID | null | undefined;
  entries: Map<UUID, DirectoryEntry>;
  loading?: boolean;
  /** Also show the email below the name. */
  detailed?: boolean;
  /** Text when there is no user (e.g. no approver yet). */
  empty?: string;
  /** Audit events by the platform itself. */
  system?: boolean;
  className?: string;
}

/** A user's display name from the directory, "You" for the current user. */
export function UserLabel({ id, entries, loading, detailed, empty = "—", system, className }: UserLabelProps) {
  const { user } = useAuth();

  if (system) {
    return (
      <span className={cn("inline-flex items-center gap-1.5 text-muted-foreground", className)}>
        <Bot className="size-3.5" aria-hidden /> CloudForge
      </span>
    );
  }
  if (!id) return <span className={cn("text-muted-foreground", className)}>{empty}</span>;

  const entry = entries.get(id);
  if (!entry) {
    if (loading) return <Skeleton className="inline-block h-4 w-24 align-middle" />;
    return (
      <span className={cn("font-mono text-xs text-muted-foreground", className)} title={id}>
        {id === user?.id ? "You" : `User ${id.slice(-6)}`}
      </span>
    );
  }

  const name = id === user?.id ? `${entry.displayName} (you)` : entry.displayName;
  return (
    <span className={cn("min-w-0", className)}>
      <span className="block truncate">
        {name}
        {entry.status === "DISABLED" ? <span className="text-muted-foreground"> · disabled</span> : null}
      </span>
      {detailed ? <span className="block truncate text-xs text-muted-foreground">{entry.email}</span> : null}
    </span>
  );
}
