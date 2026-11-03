import { Check, ShieldCheck, X } from "lucide-react";
import { useId, useState } from "react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { FormAlert } from "@/components/shared/FormAlert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useRequestMutations } from "@/features/requests/queries";
import { toApiError } from "@/lib/api/problem";
import { environmentLabel } from "@/lib/utils/labels";
import type { RequestResponse } from "@/types/api";

const MAX_COMMENT = 2000;

/**
 * Approve or reject, for the assigned approver only (the parent checks canDecide; the backend
 * enforces it). Both actions are irreversible, so each goes through a confirmation dialog that stays
 * open and disabled until the server answers. No optimistic updates.
 */
export function DecisionPanel({ request }: { request: RequestResponse }) {
  const { approve, reject } = useRequestMutations(request.id);
  const [dialog, setDialog] = useState<"approve" | "reject" | null>(null);
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const commentId = useId();

  const pending = approve.isPending || reject.isPending;
  const trimmed = comment.trim();
  const production = request.environment === "PRODUCTION";

  const open = (kind: "approve" | "reject") => {
    setError(null);
    setDialog(kind);
  };

  const confirm = () => {
    const onError = (err: unknown) => setError(toApiError(err).message);
    if (dialog === "approve") {
      approve.mutate(trimmed || undefined, {
        onSuccess: () => {
          setDialog(null);
          toast.success(`${request.requestNumber} approved. Provisioning has been queued.`);
        },
        onError,
      });
    } else if (dialog === "reject") {
      reject.mutate(trimmed, {
        onSuccess: () => {
          setDialog(null);
          toast.success(`${request.requestNumber} rejected. The requester has been notified.`);
        },
        onError,
      });
    }
  };

  return (
    <Card className="border-primary/30 bg-primary/[0.02]">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-4 text-primary" aria-hidden /> Your decision
        </CardTitle>
        <CardDescription>
          You are the assigned approver. Approving starts provisioning
          {production ? ` in ${environmentLabel(request.environment).toLowerCase()}` : ""} immediately.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor={commentId}>Comment</Label>
          <Textarea
            id={commentId}
            rows={3}
            maxLength={MAX_COMMENT}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            placeholder="Optional when approving, required when rejecting. The requester sees it."
            disabled={pending}
            aria-describedby={`${commentId}-hint`}
          />
          <p id={`${commentId}-hint`} className="text-xs text-muted-foreground">
            {trimmed.length}/{MAX_COMMENT}
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Button onClick={() => open("approve")} disabled={pending}>
            <Check aria-hidden /> Approve
          </Button>
          <Button variant="outline" onClick={() => open("reject")} disabled={pending}>
            <X aria-hidden /> Reject
          </Button>
        </div>
      </CardContent>

      <ConfirmDialog
        open={dialog === "approve"}
        onOpenChange={(next) => !next && setDialog(null)}
        title={`Approve ${request.requestNumber}?`}
        description={
          <>
            The resource will be created automatically
            {production ? <strong> in production</strong> : null}. This decision can't be undone.
          </>
        }
        confirmLabel="Approve request"
        onConfirm={confirm}
        pending={approve.isPending}
      >
        <FormAlert>{error}</FormAlert>
      </ConfirmDialog>

      <ConfirmDialog
        open={dialog === "reject"}
        onOpenChange={(next) => !next && setDialog(null)}
        title={`Reject ${request.requestNumber}?`}
        description="The requester sees your reason. A rejected request can't be reopened; they would need to submit a new one."
        confirmLabel="Reject request"
        tone="destructive"
        onConfirm={confirm}
        pending={reject.isPending}
        confirmDisabled={trimmed.length === 0}
      >
        {trimmed.length === 0 ? (
          <p className="text-sm font-medium text-destructive">Add a reason in the comment field first.</p>
        ) : (
          <blockquote className="rounded-md border-l-2 bg-muted px-3 py-2 text-sm whitespace-pre-wrap">{trimmed}</blockquote>
        )}
        <FormAlert>{error}</FormAlert>
      </ConfirmDialog>
    </Card>
  );
}
