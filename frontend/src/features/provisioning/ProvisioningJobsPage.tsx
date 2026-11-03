import { useQueryClient } from "@tanstack/react-query";
import { Cog, RotateCw } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { EmptyState } from "@/components/feedback/EmptyState";
import { FormAlert } from "@/components/shared/FormAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { ResourceTypeLabel } from "@/components/shared/ProviderIcon";
import { providerLabel, providerOf } from "@/lib/utils/labels";
import { JobStatusBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { DataTable, type Column } from "@/components/tables/DataTable";
import { FilterBar, FilterSelect } from "@/components/tables/FilterBar";
import { TablePagination } from "@/components/tables/TablePagination";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useCurrentUser } from "@/features/auth/auth-context";
import { requestsApi } from "@/features/requests/api";
import { invalidateRequest } from "@/features/requests/queries";
import { useListParams } from "@/hooks/useListParams";
import { toApiError } from "@/lib/api/problem";
import { resourceTypeLabel } from "@/lib/utils/labels";
import { hasRole } from "@/lib/utils/permissions";
import { RESOURCE_TYPE_CODES, type JobStatus, type ProvisioningJobSummary, type ResourceTypeCode } from "@/types/api";

import { useJobs } from "./api";

const VIEWS: Record<string, { label: string; statuses: JobStatus[] | undefined }> = {
  all: { label: "All", statuses: undefined },
  failed: { label: "Failed", statuses: ["FAILED"] },
  active: { label: "In progress", statuses: ["QUEUED", "RUNNING", "AWAITING_PROVIDER"] },
  succeeded: { label: "Succeeded", statuses: ["SUCCEEDED"] },
};

const FILTERS = ["view", "resourceType"] as const;

export function ProvisioningJobsPage() {
  const user = useCurrentUser();
  const isAdmin = hasRole(user, "PLATFORM_ADMIN");
  const list = useListParams(FILTERS);
  const viewKey = list.filters.view && list.filters.view in VIEWS ? list.filters.view : "all";
  const view = VIEWS[viewKey] ?? VIEWS.all;
  const resourceType = RESOURCE_TYPE_CODES.includes(list.filters.resourceType as ResourceTypeCode)
    ? (list.filters.resourceType as ResourceTypeCode)
    : undefined;

  const query = useJobs({ status: view?.statuses, resourceType, page: list.page, size: list.size });
  const [retrying, setRetrying] = useState<ProvisioningJobSummary | null>(null);

  const columns: Column<ProvisioningJobSummary>[] = [
    {
      id: "request",
      header: "Request",
      cell: (job) => (
        <Link to={`/requests/${job.requestId}`} className="font-mono text-xs font-medium text-primary underline-offset-4 hover:underline">
          {job.requestNumber}
        </Link>
      ),
    },
    { id: "provider", header: "Provider", cell: (job) => providerLabel(providerOf(job.resourceType)), hideBelow: "lg" },
    { id: "type", header: "Resource type", cell: (job) => <ResourceTypeLabel code={job.resourceType} />, hideBelow: "md" },
    { id: "status", header: "Status", cell: (job) => <JobStatusBadge status={job.status} /> },
    { id: "attempts", header: "Attempts", cell: (job) => <span className="tabular-nums">{job.attemptCount}</span>, className: "text-right" },
    { id: "queued", header: "Queued", cell: (job) => <Timestamp value={job.createdAt} mode="relative" />, hideBelow: "lg" },
    { id: "changed", header: "Last change", cell: (job) => <Timestamp value={job.updatedAt} mode="relative" />, hideBelow: "sm" },
    {
      id: "failure",
      header: "Failure reason",
      hideBelow: "xl",
      cell: (job) =>
        job.lastErrorMessage ? (
          <span className="line-clamp-2 max-w-80 text-xs" title={job.lastErrorMessage}>
            {job.lastErrorMessage}
          </span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    ...(isAdmin
      ? [
          {
            id: "actions",
            header: <span className="sr-only">Actions</span>,
            className: "text-right",
            cell: (job: ProvisioningJobSummary) =>
              job.status === "FAILED" ? (
                <Button variant="outline" size="xs" onClick={() => setRetrying(job)}>
                  <RotateCw aria-hidden /> Retry
                </Button>
              ) : null,
          },
        ]
      : []),
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Provisioning jobs"
        description={
          isAdmin
            ? "The provisioning queue. Failed jobs can be retried once the cause is fixed."
            : "The provisioning queue, read-only."
        }
      />
      <Card className="gap-0 overflow-hidden py-0">
        <FilterBar onReset={list.reset} canReset={list.hasFilters}>
          <Tabs value={viewKey} onValueChange={(value) => list.setFilter("view", value === "all" ? undefined : value)}>
            <TabsList>
              {Object.entries(VIEWS).map(([key, entry]) => (
                <TabsTrigger key={key} value={key}>
                  {entry.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
          <FilterSelect
            label="Resource type"
            allLabel="All providers"
            value={resourceType}
            onChange={(value) => list.setFilter("resourceType", value)}
            options={RESOURCE_TYPE_CODES.map((value) => ({ value, label: resourceTypeLabel(value) }))}
          />
        </FilterBar>
        <DataTable
          caption="Provisioning jobs"
          columns={columns}
          rows={query.data?.content}
          rowKey={(job) => job.jobId}
          isLoading={query.isLoading}
          isFetching={query.isFetching && !query.isLoading}
          error={query.error}
          onRetry={() => void query.refetch()}
          empty={
            <EmptyState
              icon={Cog}
              title={viewKey === "failed" ? "No failed jobs" : "No provisioning jobs"}
              description={
                viewKey === "failed"
                  ? "Every provisioning job has either succeeded or is still running."
                  : "Jobs are created when a manager approves a request."
              }
            />
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
      {retrying ? <RetryDialog job={retrying} onClose={() => setRetrying(null)} /> : null}
    </div>
  );
}

function RetryDialog({ job, onClose }: { job: ProvisioningJobSummary; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const confirm = async () => {
    setPending(true);
    setError(null);
    try {
      await requestsApi.retryProvisioning(job.requestId);
      invalidateRequest(queryClient, job.requestId);
      toast.success(`Provisioning of ${job.requestNumber} queued again`);
      onClose();
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setPending(false);
    }
  };

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={`Retry provisioning ${job.requestNumber}?`}
      description={
        <>
          The {resourceTypeLabel(job.resourceType)} job is queued again with the same specification. Last error:{" "}
          <span className="text-foreground">{job.lastErrorMessage ?? "none recorded"}</span>
        </>
      }
      confirmLabel="Retry provisioning"
      onConfirm={() => void confirm()}
      pending={pending}
    >
      <FormAlert>{error}</FormAlert>
    </ConfirmDialog>
  );
}
