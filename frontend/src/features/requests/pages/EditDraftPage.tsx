import { FilePen } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { useCurrentUser } from "@/features/auth/auth-context";
import { useCatalog } from "@/features/resources/api";
import { toApiError } from "@/lib/api/problem";
import { canEditDraft } from "@/lib/utils/permissions";

import type { RequestInput } from "../api";
import { RequestForm, type RequestFormAction } from "../components/RequestForm";
import { useRequest, useRequestMutations } from "../queries";
import { fromSpec } from "../spec";

export function EditDraftPage() {
  const { id = "" } = useParams();
  const user = useCurrentUser();
  const navigate = useNavigate();
  const request = useRequest(id);
  const catalog = useCatalog();
  const { update, submit } = useRequestMutations(id);

  if (request.isLoading || catalog.isLoading) return <PageSkeleton />;
  if (request.error) return <ErrorState error={request.error} onRetry={() => void request.refetch()} />;
  if (catalog.error) return <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />;
  if (!request.data) return null;

  const draft = request.data;
  const type = catalog.data?.find((entry) => entry.code === draft.resourceType);

  if (!canEditDraft(user, draft) || !type) {
    return (
      <EmptyState
        icon={FilePen}
        title={type ? "This request can't be edited" : "This resource type is no longer available"}
        description={
          type
            ? "Only drafts can be edited, and only by their requester."
            : "An administrator disabled this resource type. You can delete the draft."
        }
        action={
          <Button asChild variant="outline" size="sm">
            <Link to={`/requests/${draft.id}`}>Back to {draft.requestNumber}</Link>
          </Button>
        }
      />
    );
  }

  const onSave = async (action: RequestFormAction, input: RequestInput) => {
    await update.mutateAsync(input);
    if (action === "submit") {
      try {
        await submit.mutateAsync();
        toast.success(`${draft.requestNumber} submitted for approval`);
      } catch (error) {
        toast.error(`Changes saved, but ${draft.requestNumber} was not submitted`, {
          description: toApiError(error).message,
          duration: 12_000,
        });
      }
    } else {
      toast.success("Draft saved");
    }
    void navigate(`/requests/${draft.id}`);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="Edit draft" eyebrow={<span className="font-mono">{draft.requestNumber}</span>} />
      <RequestForm
        type={type}
        initialValues={{ environment: draft.environment, spec: fromSpec(draft.spec), justification: draft.justification }}
        onSave={onSave}
        saveDraftLabel="Save draft"
      />
    </div>
  );
}
