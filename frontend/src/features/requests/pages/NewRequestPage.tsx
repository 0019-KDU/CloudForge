import { useQueryClient } from "@tanstack/react-query";
import { ArrowRight, PackageX } from "lucide-react";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { PageHeader } from "@/components/shared/PageHeader";
import { ProviderIcon } from "@/components/shared/ProviderIcon";
import { providerLabel } from "@/lib/utils/labels";
import { Badge } from "@/components/ui/badge";
import { useCatalog } from "@/features/resources/api";
import { toApiError } from "@/lib/api/problem";
import { cn } from "@/lib/utils/cn";
import { environmentLabel } from "@/lib/utils/labels";
import type { ResourceTypeCode, ResourceTypeResponse } from "@/types/api";

import { requestsApi, type RequestInput } from "../api";
import { RequestForm, type RequestFormAction } from "../components/RequestForm";
import { StepIndicator } from "../components/StepIndicator";
import { invalidateRequest } from "../queries";
import { emptySpec } from "../spec";

export function NewRequestPage() {
  const catalog = useCatalog();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const selectedCode = params.get("type") as ResourceTypeCode | null;
  const selected = catalog.data?.find((type) => type.code === selectedCode && type.allowedEnvironments.length > 0);
  // Remount the form when the type changes so values from another type never leak in.
  const [formKey, setFormKey] = useState(0);

  const choose = (code: ResourceTypeCode | null) => {
    setFormKey((key) => key + 1);
    setParams(code ? { type: code } : {}, { replace: false });
  };

  const onSave = async (action: RequestFormAction, input: RequestInput) => {
    if (!selected) return;
    const created = await requestsApi.create({ ...input, resourceType: selected.code });
    invalidateRequest(queryClient);
    if (action === "draft") {
      toast.success(`Draft ${created.requestNumber} saved`);
      void navigate(`/requests/${created.id}`);
      return;
    }
    try {
      await requestsApi.submit(created.id);
      invalidateRequest(queryClient, created.id);
      toast.success(`${created.requestNumber} submitted for approval`);
    } catch (error) {
      // The draft exists; keep the user's work and explain why it isn't submitted yet.
      toast.error(`${created.requestNumber} was saved as a draft but not submitted`, {
        description: toApiError(error).message,
        duration: 12_000,
      });
    }
    void navigate(`/requests/${created.id}`);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="New request" description="Request an AWS account, a GCP project or a Kubernetes namespace." />

      {catalog.isLoading ? (
        <PageSkeleton />
      ) : catalog.error ? (
        <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
      ) : selected ? (
        <RequestForm
          key={formKey}
          type={selected}
          leadingSteps={["Resource type"]}
          onBackToLeading={() => choose(null)}
          initialValues={{
            environment: selected.allowedEnvironments.length === 1 ? (selected.allowedEnvironments[0] ?? "") : "",
            spec: emptySpec(selected.code),
            justification: "",
          }}
          onSave={onSave}
          saveDraftLabel="Save as draft"
        />
      ) : (
        <TypeChooser types={catalog.data ?? []} onChoose={choose} />
      )}
    </div>
  );
}

function TypeChooser({ types, onChoose }: { types: ResourceTypeResponse[]; onChoose: (code: ResourceTypeCode) => void }) {
  if (types.length === 0) {
    return (
      <EmptyState
        icon={PackageX}
        title="No resource types are available"
        description="Platform administrators haven't enabled any resource types yet."
      />
    );
  }
  return (
    <div className="space-y-6">
      <StepIndicator steps={["Resource type", "Configuration", "Justification", "Review"]} current={0} />
      <div role="list" className="grid gap-3">
        {types.map((type) => {
          const available = type.allowedEnvironments.length > 0;
          return (
            <div role="listitem" key={type.code}>
              <button
                type="button"
                disabled={!available}
                onClick={() => onChoose(type.code)}
                className={cn(
                  "group flex w-full items-start gap-4 rounded-lg border bg-card p-4 text-left transition-colors",
                  "focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                  available ? "hover:border-primary/50 hover:bg-accent/40" : "cursor-not-allowed opacity-60",
                )}
              >
                <ProviderIcon provider={type.provider} className="size-9" />
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{type.displayName}</span>
                    <span className="text-xs text-muted-foreground">{providerLabel(type.provider)}</span>
                  </div>
                  <p className="text-sm text-muted-foreground">{type.description}</p>
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {available ? (
                      type.allowedEnvironments.map((env) => (
                        <Badge key={env} variant="secondary" className="font-normal">
                          {environmentLabel(env)}
                        </Badge>
                      ))
                    ) : (
                      <span className="text-xs text-muted-foreground">
                        Not available yet: no environment allows requests. Ask a platform administrator.
                      </span>
                    )}
                  </div>
                </div>
                {available ? (
                  <ArrowRight className="mt-2 size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" aria-hidden />
                ) : null}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
