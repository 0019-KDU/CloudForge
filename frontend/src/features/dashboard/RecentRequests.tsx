import type { ReactNode } from "react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { TableSkeleton } from "@/components/feedback/Loaders";
import { ResourceTypeLabel } from "@/components/shared/ProviderIcon";
import { RequestStatusBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { UserLabel } from "@/components/shared/UserLabel";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { RequestListParams } from "@/features/requests/api";
import { useRequestList } from "@/features/requests/queries";
import { resourceName } from "@/features/requests/spec";
import { useDirectory } from "@/features/users/directory";
import type { LucideIcon } from "lucide-react";

interface RecentRequestsProps {
  title: string;
  params: RequestListParams;
  viewAll: string;
  showRequester?: boolean;
  empty: { icon: LucideIcon; title: string; description: ReactNode; action?: ReactNode };
}

export function RecentRequests({ title, params, viewAll, showRequester, empty }: RecentRequestsProps) {
  const query = useRequestList({ ...params, page: 0, size: 5 });
  const rows = query.data?.content ?? [];
  const people = useDirectory(showRequester ? rows.map((row) => row.requesterId) : []);

  return (
    <Card className="gap-0 py-0">
      <CardHeader className="border-b py-4">
        <CardTitle className="text-sm font-semibold">
          <h2>{title}</h2>
        </CardTitle>
        <CardAction>
          <Button variant="ghost" size="sm" asChild>
            <Link to={viewAll}>View all</Link>
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="px-0">
        {query.isLoading ? (
          <TableSkeleton rows={3} columns={3} />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => void query.refetch()} className="py-8" />
        ) : rows.length === 0 ? (
          <EmptyState {...empty} className="py-10" />
        ) : (
          <ul className="divide-y">
            {rows.map((row) => (
              <li key={row.id}>
                <Link
                  to={`/requests/${row.id}`}
                  className="flex items-center gap-4 px-6 py-3 transition-colors hover:bg-accent/50 focus-visible:bg-accent focus-visible:outline-none"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="font-mono text-xs font-medium">{row.requestNumber}</span>
                      <span className="truncate font-mono text-xs text-muted-foreground">
                        {resourceName(row.resourceType, row.spec)}
                      </span>
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-x-3 text-xs text-muted-foreground">
                      <ResourceTypeLabel code={row.resourceType} />
                      {showRequester ? <UserLabel id={row.requesterId} entries={people.entries} loading={people.isLoading} /> : null}
                    </div>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <RequestStatusBadge status={row.status} />
                    <span className="text-xs text-muted-foreground">
                      <Timestamp value={row.submittedAt ?? row.createdAt} mode="relative" />
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
