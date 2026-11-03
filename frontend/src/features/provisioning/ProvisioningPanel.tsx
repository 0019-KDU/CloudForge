import { RotateCw } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ErrorState } from "@/components/feedback/ErrorState";
import { TableSkeleton } from "@/components/feedback/Loaders";
import { FormAlert } from "@/components/shared/FormAlert";
import { JsonObjectList, KeyValueList } from "@/components/shared/KeyValueList";
import { JobStatusBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useCurrentUser } from "@/features/auth/auth-context";
import { IN_FLIGHT, useProvisioning, useRequestMutations } from "@/features/requests/queries";
import { toApiError } from "@/lib/api/problem";
import { humanize } from "@/lib/utils/labels";
import { canRetryProvisioning } from "@/lib/utils/permissions";
import type { RequestResponse } from "@/types/api";

import { jobTimes } from "./job-times";

export function ProvisioningPanel({ request }: { request: RequestResponse }) {
  const user = useCurrentUser();
  const polling = IN_FLIGHT.includes(request.status);
  const query = useProvisioning(request.id, true, polling);
  const { retry } = useRequestMutations(request.id);
  const [confirming, setConfirming] = useState(false);
  const [retryError, setRetryError] = useState<string | null>(null);

  if (query.isLoading) return <TableSkeleton rows={3} columns={4} />;
  if (query.error) return <ErrorState error={query.error} onRetry={() => void query.refetch()} className="py-6" />;
  const job = query.data;
  if (!job) return <p className="text-sm text-muted-foreground">Provisioning hasn't been queued yet.</p>;

  const { startedAt, completedAt } = jobTimes(job);
  const canRetry = canRetryProvisioning(user, request) && job.status === "FAILED";

  const confirmRetry = () => {
    setRetryError(null);
    retry.mutate(undefined, {
      onSuccess: () => {
        setConfirming(false);
        toast.success(`Provisioning of ${request.requestNumber} queued again`);
        void query.refetch();
      },
      onError: (error) => setRetryError(toApiError(error).message),
    });
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <KeyValueList
          columns={3}
          className="flex-1"
          items={[
            { label: "Job status", value: <JobStatusBadge status={job.status} /> },
            { label: "Attempts", value: <span className="tabular-nums">{job.attemptCount}</span> },
            {
              label: job.status === "FAILED" || job.status === "SUCCEEDED" ? "Completed" : "Next check",
              value: <Timestamp value={completedAt ?? job.nextRunAt} empty="—" />,
            },
            { label: "Started", value: <Timestamp value={startedAt} empty="Not started" /> },
          ]}
        />
        {canRetry ? (
          <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
            <RotateCw aria-hidden /> Retry provisioning
          </Button>
        ) : null}
      </div>

      {job.awaitingProvider ? (
        <p className="rounded-md bg-info-subtle px-3 py-2 text-sm text-info">
          The provider is creating the resource. This can take several minutes; the page updates by itself.
        </p>
      ) : null}

      {job.status === "FAILED" && job.lastErrorMessage ? (
        <div className="rounded-md border border-danger/30 bg-danger-subtle px-3 py-2 text-sm">
          <p className="font-medium text-danger">Provisioning failed</p>
          <p className="mt-0.5">{job.lastErrorMessage}</p>
          {job.lastErrorCode ? <p className="mt-1 font-mono text-xs text-muted-foreground">{job.lastErrorCode}</p> : null}
          {!canRetry ? (
            <p className="mt-1 text-xs text-muted-foreground">A platform administrator can retry once the cause is fixed.</p>
          ) : null}
        </div>
      ) : null}

      {job.result ? (
        <section className="space-y-3 rounded-md border bg-success-subtle/40 p-4">
          <h3 className="text-sm font-medium">Created resource</h3>
          <KeyValueList
            items={[
              { label: "Resource ID", value: <code className="font-mono text-xs break-all">{job.result.externalResourceId}</code> },
              { label: "Created", value: <Timestamp value={job.result.createdAt} /> },
            ]}
          />
          <JsonObjectList value={job.result.details} />
        </section>
      ) : null}

      {job.attempts.length > 0 ? (
        <section className="space-y-2">
          <h3 className="text-sm font-medium">Attempts</h3>
          <div className="rounded-md border">
            <Table>
              <caption className="sr-only">Provisioning attempts</caption>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">#</TableHead>
                  <TableHead>Step</TableHead>
                  <TableHead>Outcome</TableHead>
                  <TableHead className="hidden sm:table-cell">Finished</TableHead>
                  <TableHead className="hidden md:table-cell">Error</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {job.attempts.map((attempt, index) => (
                  <TableRow key={`${attempt.attemptNumber}-${index}`}>
                    <TableCell className="tabular-nums">{attempt.attemptNumber}</TableCell>
                    <TableCell>{attempt.kind === "START" ? "Start" : "Status check"}</TableCell>
                    <TableCell>{humanize(attempt.outcome)}</TableCell>
                    <TableCell className="hidden sm:table-cell">
                      <Timestamp value={attempt.finishedAt} />
                    </TableCell>
                    <TableCell className="hidden max-w-80 text-xs text-muted-foreground md:table-cell">
                      {attempt.errorMessage ?? "—"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </section>
      ) : null}

      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={`Retry provisioning ${request.requestNumber}?`}
        description="The job is queued again and the provider is called with the same specification. Make sure the cause of the failure has been fixed."
        confirmLabel="Retry provisioning"
        onConfirm={confirmRetry}
        pending={retry.isPending}
      >
        <FormAlert>{retryError}</FormAlert>
      </ConfirmDialog>
    </div>
  );
}
