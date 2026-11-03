import { PackageX, Plus } from "lucide-react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { PageHeader } from "@/components/shared/PageHeader";
import { ProviderIcon } from "@/components/shared/ProviderIcon";
import { providerLabel } from "@/lib/utils/labels";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useCurrentUser } from "@/features/auth/auth-context";
import { parseConstraints } from "@/features/requests/spec";
import { environmentLabel } from "@/lib/utils/labels";
import { canCreateRequests } from "@/lib/utils/permissions";
import { ENVIRONMENTS, type EnvironmentPolicy, type ResourceTypeCode, type ResourceTypeResponse } from "@/types/api";

import { useCatalog } from "./api";

export function CatalogPage() {
  const catalog = useCatalog();
  const user = useCurrentUser();
  const canRequest = canCreateRequests(user);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Resource catalog"
        description="What can be requested, in which environments, and within which limits."
      />
      {catalog.isLoading ? (
        <PageSkeleton />
      ) : catalog.error ? (
        <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
      ) : !catalog.data?.length ? (
        <EmptyState icon={PackageX} title="No resource types are enabled" description="Platform administrators enable resource types in the catalog settings." />
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          {catalog.data.map((type) => (
            <CatalogCard key={type.code} type={type} canRequest={canRequest} />
          ))}
        </div>
      )}
    </div>
  );
}

function CatalogCard({ type, canRequest }: { type: ResourceTypeResponse; canRequest: boolean }) {
  const available = type.allowedEnvironments.length > 0;
  return (
    <Card className="flex flex-col">
      <CardHeader>
        <div className="flex items-center gap-3">
          <ProviderIcon provider={type.provider} className="size-9" />
          <div>
            <CardTitle className="text-base">
              <h2>{type.displayName}</h2>
            </CardTitle>
            <p className="text-xs text-muted-foreground">{providerLabel(type.provider)}</p>
          </div>
        </div>
        <CardDescription className="pt-2">{type.description}</CardDescription>
      </CardHeader>
      <CardContent className="flex-1">
        <ul className="divide-y rounded-md border text-sm">
          {ENVIRONMENTS.map((env) => {
            const policy = type.environments.find((p) => p.environment === env);
            return (
              <li key={env} className="px-3 py-2.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium">{environmentLabel(env)}</span>
                  <span className={policy ? "text-xs text-success" : "text-xs text-muted-foreground"}>
                    {policy ? "Available" : "Not available"}
                  </span>
                </div>
                {policy ? <PolicyLimits code={type.code} policy={policy} /> : null}
              </li>
            );
          })}
        </ul>
      </CardContent>
      {canRequest ? (
        <CardFooter>
          <Button asChild={available} variant="outline" className="w-full" disabled={!available}>
            {available ? (
              <Link to={`/requests/new?type=${type.code}`}>
                <Plus aria-hidden /> Request {type.displayName}
              </Link>
            ) : (
              <span>Not available yet</span>
            )}
          </Button>
        </CardFooter>
      ) : null}
    </Card>
  );
}

function PolicyLimits({ code, policy }: { code: ResourceTypeCode; policy: EnvironmentPolicy }) {
  const c = parseConstraints(policy.constraints);
  const lines: string[] = [];
  if (policy.maxActivePerUser) lines.push(`Up to ${policy.maxActivePerUser} active per person`);
  if (code === "AWS_ACCOUNT") {
    lines.push(`${c.allowedOrganizationalUnitIds.length} approved organizational unit${c.allowedOrganizationalUnitIds.length === 1 ? "" : "s"}`);
    if (c.allowedEmailDomains.length) lines.push(`Email: ${c.allowedEmailDomains.map((d) => `@${d}`).join(", ")}`);
  }
  if (code === "GCP_PROJECT") lines.push(`${c.allowedFolderIds.length} approved folder${c.allowedFolderIds.length === 1 ? "" : "s"}`);
  if (code === "K8S_NAMESPACE") {
    if (c.maxCpuCores) lines.push(`Up to ${c.maxCpuCores} CPU cores`);
    if (c.maxMemoryGi) lines.push(`Up to ${c.maxMemoryGi} GiB memory`);
  }
  if (lines.length === 0) return null;
  return (
    <ul className="mt-1 space-y-0.5 text-xs text-muted-foreground">
      {lines.map((line) => (
        <li key={line}>{line}</li>
      ))}
    </ul>
  );
}
