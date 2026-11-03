import { CheckCircle, Clock, XCircle } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";
import { Timestamp } from "@/components/shared/Timestamp";
import { UserLabel } from "@/components/shared/UserLabel";
import { ErrorState } from "@/components/feedback/ErrorState";
import type { DecisionResponse, DirectoryEntry, RequestResponse, UUID } from "@/types/api";

import { useDecision } from "../queries";

interface DecisionSummaryProps {
  request: RequestResponse;
  people: { entries: Map<UUID, DirectoryEntry>; isLoading: boolean };
}

const DECIDED = new Set(["APPROVED", "REJECTED", "PROVISIONING", "PROVISIONED", "PROVISIONING_FAILED"]);

export function DecisionSummary({ request, people }: DecisionSummaryProps) {
  const decided = DECIDED.has(request.status);
  const decision = useDecision(request.id, decided);

  if (request.status === "DRAFT") {
    return <p className="text-sm text-muted-foreground">Not submitted yet. Submitting sends it to your manager.</p>;
  }
  if (request.status === "CANCELLED") {
    return <p className="text-sm text-muted-foreground">Cancelled by the requester before a decision.</p>;
  }
  if (!decided) {
    return (
      <p className="flex items-center gap-2 text-sm">
        <Clock className="size-4 text-warning" aria-hidden />
        <span>
          Waiting for{" "}
          <UserLabel id={request.assignedApproverId} entries={people.entries} loading={people.isLoading} className="inline font-medium" />
        </span>
      </p>
    );
  }
  if (decision.isLoading) return <Skeleton className="h-12 w-full" />;
  if (decision.error) return <ErrorState error={decision.error} onRetry={() => void decision.refetch()} className="py-4" />;
  if (!decision.data) return <p className="text-sm text-muted-foreground">No decision recorded.</p>;

  return <DecisionDetails decision={decision.data} people={people} />;
}

function DecisionDetails({ decision, people }: { decision: DecisionResponse; people: DecisionSummaryProps["people"] }) {
  const approved = decision.decision === "APPROVED";
  return (
    <div className="space-y-2">
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        {approved ? (
          <CheckCircle className="size-4 text-success" aria-hidden />
        ) : (
          <XCircle className="size-4 text-danger" aria-hidden />
        )}
        <span className="font-medium">{approved ? "Approved" : "Rejected"}</span>
        <span className="text-muted-foreground">by</span>
        <UserLabel id={decision.approverId} entries={people.entries} loading={people.isLoading} className="inline" />
        <span className="text-muted-foreground">
          · <Timestamp value={decision.decidedAt} />
        </span>
      </p>
      {decision.comment ? (
        <blockquote className="rounded-md border-l-2 bg-muted px-3 py-2 text-sm whitespace-pre-wrap">{decision.comment}</blockquote>
      ) : null}
    </div>
  );
}
