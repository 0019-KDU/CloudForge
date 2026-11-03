import { History } from "lucide-react";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { TableSkeleton } from "@/components/feedback/Loaders";
import { JsonValue } from "@/components/shared/KeyValueList";
import { Timestamp } from "@/components/shared/Timestamp";
import { UserLabel } from "@/components/shared/UserLabel";
import { useDirectory } from "@/features/users/directory";
import { humanizeKey } from "@/lib/utils/labels";
import { cn } from "@/lib/utils/cn";
import type { AuditEventResponse, PageResponse } from "@/types/api";

import { auditActionLabel, auditActionTone, type ActionTone } from "./labels";

const DOT: Record<ActionTone, string> = {
  neutral: "bg-neutral",
  success: "bg-success",
  danger: "bg-danger",
  warning: "bg-warning",
  info: "bg-info",
};

/** Detail keys that hold user IDs are shown as names. */
const USER_ID_KEYS = new Set(["approverId", "managerId", "previousManagerId", "userId"]);

interface AuditTimelineProps {
  data: PageResponse<AuditEventResponse> | undefined;
  isLoading: boolean;
  error: unknown;
  onRetry: () => void;
}

/** Chronological history of one entity (oldest first, as the backend returns it). */
export function AuditTimeline({ data, isLoading, error, onRetry }: AuditTimelineProps) {
  const events = data?.content ?? [];
  const people = useDirectory(
    events.flatMap((event) => [
      event.actorId,
      ...Object.entries(event.details)
        .filter(([key, value]) => USER_ID_KEYS.has(key) && typeof value === "string")
        .map(([, value]) => value as string),
    ]),
  );

  if (isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (events.length === 0) {
    return <EmptyState icon={History} title="No history yet" description="Changes to this request will appear here." />;
  }

  return (
    <div>
      <ol className="relative space-y-5 border-l pl-6">
        {events.map((event) => {
          const details = Object.entries(event.details).filter(([key]) => key !== "requestNumber");
          return (
            <li key={event.id} className="relative">
              <span
                className={cn("absolute top-1.5 -left-[29px] size-2.5 rounded-full ring-4 ring-card", DOT[auditActionTone(event.action)])}
                aria-hidden
              />
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <p className="text-sm font-medium">{auditActionLabel(event.action)}</p>
                <span className="text-xs text-muted-foreground">
                  <Timestamp value={event.occurredAt} />
                </span>
              </div>
              <p className="text-xs text-muted-foreground">
                by{" "}
                <UserLabel
                  id={event.actorId}
                  entries={people.entries}
                  loading={people.isLoading}
                  system={event.actorType === "SYSTEM"}
                  className="inline"
                />
              </p>
              {details.length > 0 ? (
                <dl className="mt-2 grid gap-1 text-xs">
                  {details.map(([key, value]) => (
                    <div key={key} className="flex flex-wrap gap-x-2">
                      <dt className="text-muted-foreground">{humanizeKey(key)}:</dt>
                      <dd className="min-w-0">
                        {USER_ID_KEYS.has(key) && typeof value === "string" ? (
                          <UserLabel id={value} entries={people.entries} loading={people.isLoading} className="inline" />
                        ) : (
                          <JsonValue value={value} />
                        )}
                      </dd>
                    </div>
                  ))}
                </dl>
              ) : null}
            </li>
          );
        })}
      </ol>
      {data && data.totalElements > events.length ? (
        <p className="mt-4 text-xs text-muted-foreground">
          Showing the first {events.length} of {data.totalElements} events. The full history is in the audit log.
        </p>
      ) : null}
    </div>
  );
}
