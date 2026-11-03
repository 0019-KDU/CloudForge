import { Ban, Pencil, Send, Trash2 } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { FormAlert } from "@/components/shared/FormAlert";
import { JsonObjectList, KeyValueList } from "@/components/shared/KeyValueList";
import { PageHeader } from "@/components/shared/PageHeader";
import { ResourceTypeLabel } from "@/components/shared/ProviderIcon";
import { EnvironmentBadge, RequestStatusBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { UserLabel } from "@/components/shared/UserLabel";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DecisionPanel } from "@/features/approvals/DecisionPanel";
import { AuditTimeline } from "@/features/audit/AuditTimeline";
import { useCurrentUser } from "@/features/auth/auth-context";
import { ProvisioningPanel } from "@/features/provisioning/ProvisioningPanel";
import { useDirectory } from "@/features/users/directory";
import { toApiError } from "@/lib/api/problem";
import { resourceTypeLabel } from "@/lib/utils/labels";
import { canCancel, canDecide, canEditDraft } from "@/lib/utils/permissions";
import type { RequestResponse, RequestStatus } from "@/types/api";

import { DecisionSummary } from "../components/DecisionSummary";
import { RequestLifecycle } from "../components/RequestLifecycle";
import { IN_FLIGHT, useRequest, useRequestAudit, useRequestMutations } from "../queries";
import { SPEC_LABELS } from "../spec";

const PROVISIONING_STATES: readonly RequestStatus[] = ["APPROVED", "PROVISIONING", "PROVISIONED", "PROVISIONING_FAILED"];

export function RequestDetailPage() {
  const { id = "" } = useParams();
  const request = useRequest(id);

  if (request.isLoading) return <PageSkeleton />;
  if (request.error) return <ErrorState error={request.error} onRetry={() => void request.refetch()} />;
  if (!request.data) return null;
  return <RequestDetail request={request.data} />;
}

function RequestDetail({ request }: { request: RequestResponse }) {
  const user = useCurrentUser();
  const people = useDirectory([request.requesterId, request.assignedApproverId]);
  const audit = useRequestAudit(request.id, IN_FLIGHT.includes(request.status));
  const deciding = canDecide(user, request);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<span className="font-mono">{request.requestNumber}</span>}
        title={`${resourceTypeLabel(request.resourceType)} request`}
        description={<RequestStatusBadge status={request.status} />}
        actions={<RequesterActions request={request} />}
      />

      <Card>
        <CardContent className="py-1">
          <RequestLifecycle request={request} />
        </CardContent>
      </Card>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {deciding ? <DecisionPanel request={request} /> : null}

          <Section title="Summary">
            <KeyValueList
              items={[
                { label: "Resource type", value: <ResourceTypeLabel code={request.resourceType} withProvider /> },
                { label: "Environment", value: <EnvironmentBadge environment={request.environment} /> },
                {
                  label: "Requester",
                  value: <UserLabel id={request.requesterId} entries={people.entries} loading={people.isLoading} detailed />,
                },
                {
                  label: "Approver",
                  value: (
                    <UserLabel
                      id={request.assignedApproverId}
                      entries={people.entries}
                      loading={people.isLoading}
                      detailed
                      empty="Assigned on submission"
                    />
                  ),
                },
                { label: "Created", value: <Timestamp value={request.createdAt} /> },
                { label: "Submitted", value: <Timestamp value={request.submittedAt} empty="Not submitted" /> },
              ]}
            />
          </Section>

          <Section title="Configuration">
            <JsonObjectList value={request.spec} labels={SPEC_LABELS} />
          </Section>

          <Section title="Business justification">
            <p className="text-sm whitespace-pre-wrap">{request.justification}</p>
          </Section>

          {PROVISIONING_STATES.includes(request.status) ? (
            <Section title="Provisioning">
              <ProvisioningPanel request={request} />
            </Section>
          ) : null}
        </div>

        <div className="space-y-6">
          <Section title="Decision">
            <DecisionSummary request={request} people={people} />
          </Section>
          <Section title="History">
            <AuditTimeline
              data={audit.data}
              isLoading={audit.isLoading}
              error={audit.error}
              onRetry={() => void audit.refetch()}
            />
          </Section>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">
          <h2>{title}</h2>
        </CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

/** Edit / submit / delete for drafts and cancel for pending requests, for the requester only. */
function RequesterActions({ request }: { request: RequestResponse }) {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const { submit, cancel, remove } = useRequestMutations(request.id);
  const [dialog, setDialog] = useState<"submit" | "cancel" | "delete" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const editable = canEditDraft(user, request);
  const cancellable = canCancel(user, request);
  if (!editable && !cancellable) return null;

  const run = () => {
    setError(null);
    const onError = (err: unknown) => setError(toApiError(err).message);
    if (dialog === "submit") {
      submit.mutate(undefined, {
        onSuccess: () => {
          setDialog(null);
          toast.success(`${request.requestNumber} submitted for approval`);
        },
        onError,
      });
    } else if (dialog === "cancel") {
      cancel.mutate(undefined, {
        onSuccess: () => {
          setDialog(null);
          toast.success(`${request.requestNumber} cancelled`);
        },
        onError,
      });
    } else if (dialog === "delete") {
      remove.mutate(undefined, {
        onSuccess: () => {
          toast.success(`Draft ${request.requestNumber} deleted`);
          void navigate("/requests", { replace: true });
        },
        onError,
      });
    }
  };

  const open = (kind: NonNullable<typeof dialog>) => {
    setError(null);
    setDialog(kind);
  };

  return (
    <>
      {editable ? (
        <>
          <Button variant="ghost" onClick={() => open("delete")}>
            <Trash2 aria-hidden /> Delete
          </Button>
          <Button variant="outline" asChild>
            <Link to={`/requests/${request.id}/edit`}>
              <Pencil aria-hidden /> Edit
            </Link>
          </Button>
          <Button onClick={() => open("submit")}>
            <Send aria-hidden /> Submit for approval
          </Button>
        </>
      ) : null}
      {cancellable ? (
        <Button variant="outline" onClick={() => open("cancel")}>
          <Ban aria-hidden /> Cancel request
        </Button>
      ) : null}

      <ConfirmDialog
        open={dialog === "submit"}
        onOpenChange={(next) => !next && setDialog(null)}
        title={`Submit ${request.requestNumber}?`}
        description="Your manager will be asked to approve it. After submission you can cancel it, but not edit it."
        confirmLabel="Submit"
        onConfirm={run}
        pending={submit.isPending}
      >
        <FormAlert>{error}</FormAlert>
      </ConfirmDialog>
      <ConfirmDialog
        open={dialog === "cancel"}
        onOpenChange={(next) => !next && setDialog(null)}
        title={`Cancel ${request.requestNumber}?`}
        description="The request is withdrawn from approval. This can't be undone; you would need to create a new request."
        confirmLabel="Cancel request"
        tone="destructive"
        onConfirm={run}
        pending={cancel.isPending}
      >
        <FormAlert>{error}</FormAlert>
      </ConfirmDialog>
      <ConfirmDialog
        open={dialog === "delete"}
        onOpenChange={(next) => !next && setDialog(null)}
        title={`Delete draft ${request.requestNumber}?`}
        description="The draft is removed. Its creation and deletion stay in the audit log."
        confirmLabel="Delete draft"
        tone="destructive"
        onConfirm={run}
        pending={remove.isPending}
      >
        <FormAlert>{error}</FormAlert>
      </ConfirmDialog>
    </>
  );
}
