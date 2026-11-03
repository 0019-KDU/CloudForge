import type { ReactNode } from "react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ResourceTypeLabel } from "@/components/shared/ProviderIcon";
import { EnvironmentBadge, RequestStatusBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { UserLabel } from "@/components/shared/UserLabel";
import { DataTable, type Column } from "@/components/tables/DataTable";
import { FilterBar, FilterSelect, SearchInput } from "@/components/tables/FilterBar";
import { TablePagination } from "@/components/tables/TablePagination";
import { Card } from "@/components/ui/card";
import { useDirectory } from "@/features/users/directory";
import { useListParams } from "@/hooks/useListParams";
import { environmentLabel, resourceTypeLabel, statusLabel } from "@/lib/utils/labels";
import {
  ENVIRONMENTS,
  RESOURCE_TYPE_CODES,
  type Environment,
  type RequestListScope,
  type RequestResponse,
  type RequestStatus,
  type ResourceTypeCode,
} from "@/types/api";
import { SearchX, type LucideIcon } from "lucide-react";

import { useRequestList } from "../queries";
import { resourceName } from "../spec";

export type RequestColumn =
  | "number"
  | "resource"
  | "type"
  | "environment"
  | "requester"
  | "approver"
  | "justification"
  | "status"
  | "created"
  | "submitted"
  | "updated";

interface RequestListViewProps {
  caption: string;
  scope: RequestListScope;
  columns: RequestColumn[];
  /** Statuses always applied (e.g. pending approvals); the status filter narrows within them. */
  baseStatuses?: RequestStatus[];
  /** Options for the status filter; omit to hide it. */
  statusOptions?: RequestStatus[];
  defaultSort?: { field: string; direction: "asc" | "desc" };
  empty: { icon: LucideIcon; title: string; description: ReactNode; action?: ReactNode };
}

const FILTERS = ["q", "status", "resourceType", "environment"] as const;

export function RequestListView({ caption, scope, columns, baseStatuses, statusOptions, defaultSort, empty }: RequestListViewProps) {
  const list = useListParams(FILTERS, defaultSort ? { sort: defaultSort } : {});
  const { q, status, resourceType, environment } = list.filters;

  const statusFilter = status && (!statusOptions || statusOptions.includes(status as RequestStatus)) ? [status as RequestStatus] : baseStatuses;
  const query = useRequestList({
    scope,
    status: statusFilter,
    resourceType: RESOURCE_TYPE_CODES.includes(resourceType as ResourceTypeCode) ? (resourceType as ResourceTypeCode) : undefined,
    environment: ENVIRONMENTS.includes(environment as Environment) ? (environment as Environment) : undefined,
    q,
    page: list.page,
    size: list.size,
    sort: list.sortParam,
  });

  const rows = query.data?.content;
  const people = useDirectory(
    rows?.flatMap((row) => [
      columns.includes("requester") ? row.requesterId : null,
      columns.includes("approver") ? row.assignedApproverId : null,
    ]) ?? [],
  );

  const all: Record<RequestColumn, Column<RequestResponse>> = {
    number: {
      id: "number",
      header: "Request",
      sortField: "requestNumber",
      cell: (row) => (
        <Link
          to={`/requests/${row.id}`}
          className="font-mono text-xs font-medium text-primary underline-offset-4 hover:underline focus-visible:underline"
        >
          {row.requestNumber}
        </Link>
      ),
    },
    resource: {
      id: "resource",
      header: "Resource",
      cell: (row) => <span className="font-mono text-xs">{resourceName(row.resourceType, row.spec) ?? "—"}</span>,
    },
    type: { id: "type", header: "Type", cell: (row) => <ResourceTypeLabel code={row.resourceType} />, hideBelow: "md" },
    environment: {
      id: "environment",
      header: "Environment",
      cell: (row) => <EnvironmentBadge environment={row.environment} />,
      hideBelow: "lg",
    },
    requester: {
      id: "requester",
      header: "Requester",
      cell: (row) => <UserLabel id={row.requesterId} entries={people.entries} loading={people.isLoading} />,
      className: "max-w-48",
    },
    approver: {
      id: "approver",
      header: "Approver",
      cell: (row) => (
        <UserLabel id={row.assignedApproverId} entries={people.entries} loading={people.isLoading} empty="Not assigned" />
      ),
      hideBelow: "xl",
      className: "max-w-48",
    },
    justification: {
      id: "justification",
      header: "Justification",
      cell: (row) => (
        <span className="line-clamp-2 max-w-72 text-xs text-muted-foreground" title={row.justification}>
          {row.justification}
        </span>
      ),
      hideBelow: "xl",
    },
    status: { id: "status", header: "Status", sortField: "status", cell: (row) => <RequestStatusBadge status={row.status} /> },
    created: { id: "created", header: "Created", sortField: "createdAt", cell: (row) => <Timestamp value={row.createdAt} mode="relative" />, hideBelow: "sm" },
    submitted: {
      id: "submitted",
      header: "Submitted",
      sortField: "submittedAt",
      cell: (row) => <Timestamp value={row.submittedAt} mode="relative" empty="Not submitted" />,
      hideBelow: "sm",
    },
    updated: { id: "updated", header: "Last change", cell: (row) => <Timestamp value={row.updatedAt} mode="relative" />, hideBelow: "sm" },
  };

  return (
    <Card className="gap-0 overflow-hidden py-0">
      <FilterBar onReset={list.reset} canReset={list.hasFilters}>
        <SearchInput
          label="Search requests"
          placeholder="Search number or justification"
          value={q}
          onChange={(value) => list.setFilter("q", value)}
        />
        {statusOptions ? (
          <FilterSelect
            label="Status"
            allLabel="All statuses"
            value={status}
            onChange={(value) => list.setFilter("status", value)}
            options={statusOptions.map((value) => ({ value, label: statusLabel(value) }))}
          />
        ) : null}
        <FilterSelect
          label="Resource type"
          allLabel="All providers"
          value={resourceType}
          onChange={(value) => list.setFilter("resourceType", value)}
          options={RESOURCE_TYPE_CODES.map((value) => ({ value, label: resourceTypeLabel(value) }))}
        />
        <FilterSelect
          label="Environment"
          allLabel="All environments"
          value={environment}
          onChange={(value) => list.setFilter("environment", value)}
          options={ENVIRONMENTS.map((value) => ({ value, label: environmentLabel(value) }))}
          className="sm:w-40"
        />
      </FilterBar>

      <DataTable
        caption={caption}
        columns={columns.map((column) => all[column])}
        rows={rows}
        rowKey={(row) => row.id}
        isLoading={query.isLoading}
        isFetching={query.isFetching && !query.isLoading}
        error={query.error}
        onRetry={() => void query.refetch()}
        sort={list.sort}
        onSortChange={list.setSort}
        empty={
          list.hasFilters ? (
            <EmptyState
              icon={SearchX}
              title="No matching requests"
              description="Try a different search or clear the filters."
            />
          ) : (
            <EmptyState {...empty} />
          )
        }
      />

      {query.data ? (
        <TablePagination
          page={query.data.page}
          size={query.data.size}
          totalElements={query.data.totalElements}
          totalPages={query.data.totalPages}
          onPageChange={list.setPage}
          onSizeChange={list.setSize}
        />
      ) : null}
    </Card>
  );
}
