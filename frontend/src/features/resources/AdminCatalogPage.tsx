import { Check, Loader2, PackageX, Pencil, X } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { PageHeader } from "@/components/shared/PageHeader";
import { ProviderIcon } from "@/components/shared/ProviderIcon";
import { providerLabel } from "@/lib/utils/labels";
import { ToneBadge } from "@/components/shared/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { parseConstraints } from "@/features/requests/spec";
import { toApiError } from "@/lib/api/problem";
import { environmentLabel } from "@/lib/utils/labels";
import type { AdminResourceTypeResponse, JsonObject, PolicyResponse, ResourceTypeCode } from "@/types/api";

import { useAdminCatalog, useCatalogAdminMutations } from "./admin-api";

/** Resource types and per-environment policies: where each type may be requested, and within which limits. */
export function AdminCatalogPage() {
  const catalog = useAdminCatalog();
  const [editingType, setEditingType] = useState<AdminResourceTypeResponse | null>(null);
  const [editingPolicy, setEditingPolicy] = useState<{ type: AdminResourceTypeResponse; policy: PolicyResponse } | null>(null);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Resource policies"
        description="Enable resource types and decide, per environment, whether they can be requested and within which limits. Changes apply to new submissions and approvals."
      />
      {catalog.isLoading ? (
        <PageSkeleton />
      ) : catalog.error ? (
        <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
      ) : !catalog.data?.length ? (
        <EmptyState icon={PackageX} title="No resource types" description="Resource types are defined by the backend." />
      ) : (
        <div className="space-y-4">
          {catalog.data.map((type) => (
            <Card key={type.code}>
              <CardHeader>
                <div className="flex items-center gap-3">
                  <ProviderIcon provider={type.provider} className="size-9" />
                  <div>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <h2>{type.displayName}</h2>
                      {type.enabled ? <ToneBadge tone="success" label="Enabled" /> : <ToneBadge tone="neutral" label="Disabled" />}
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      {providerLabel(type.provider)} · <code className="font-mono">{type.code}</code>
                    </p>
                  </div>
                </div>
                <CardDescription className="pt-2">{type.description}</CardDescription>
                <CardAction>
                  <Button variant="ghost" size="sm" onClick={() => setEditingType(type)}>
                    <Pencil aria-hidden /> Edit details
                  </Button>
                </CardAction>
              </CardHeader>
              <CardContent>
                <div className="rounded-md border">
                  <Table>
                    <caption className="sr-only">{type.displayName} policies</caption>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Environment</TableHead>
                        <TableHead>Requests</TableHead>
                        <TableHead className="hidden sm:table-cell">Per-person limit</TableHead>
                        <TableHead className="hidden md:table-cell">Constraints</TableHead>
                        <TableHead>
                          <span className="sr-only">Actions</span>
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {type.policies.map((policy) => (
                        <TableRow key={policy.environment}>
                          <TableCell className="font-medium">{environmentLabel(policy.environment)}</TableCell>
                          <TableCell>
                            {policy.allowed ? (
                              <span className="inline-flex items-center gap-1 text-success">
                                <Check className="size-3.5" aria-hidden /> Allowed
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-muted-foreground">
                                <X className="size-3.5" aria-hidden /> Not allowed
                              </span>
                            )}
                          </TableCell>
                          <TableCell className="hidden tabular-nums sm:table-cell">
                            {policy.maxActivePerUser ?? <span className="text-muted-foreground">No limit</span>}
                          </TableCell>
                          <TableCell className="hidden text-xs text-muted-foreground md:table-cell">
                            {constraintSummary(type.code, policy.constraints)}
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              variant="outline"
                              size="xs"
                              onClick={() => setEditingPolicy({ type, policy })}
                              aria-label={`Edit ${environmentLabel(policy.environment)} policy for ${type.displayName}`}
                            >
                              <Pencil aria-hidden /> Edit policy
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
      {editingType ? <TypeDialog type={editingType} onClose={() => setEditingType(null)} /> : null}
      {editingPolicy ? (
        <PolicyDialog type={editingPolicy.type} policy={editingPolicy.policy} onClose={() => setEditingPolicy(null)} />
      ) : null}
    </div>
  );
}

function constraintSummary(code: ResourceTypeCode, constraints: JsonObject | null): string {
  const c = parseConstraints(constraints);
  if (code === "AWS_ACCOUNT") {
    const parts = [`${c.allowedOrganizationalUnitIds.length} OU(s)`];
    if (c.allowedEmailDomains.length) parts.push(c.allowedEmailDomains.map((d) => `@${d}`).join(", "));
    return parts.join(" · ");
  }
  if (code === "GCP_PROJECT") return `${c.allowedFolderIds.length} folder(s)`;
  const parts = [];
  if (c.maxCpuCores) parts.push(`≤ ${c.maxCpuCores} CPU`);
  if (c.maxMemoryGi) parts.push(`≤ ${c.maxMemoryGi} GiB`);
  return parts.join(" · ") || "—";
}

function TypeDialog({ type, onClose }: { type: AdminResourceTypeResponse; onClose: () => void }) {
  const { updateType } = useCatalogAdminMutations();
  const [displayName, setDisplayName] = useState(type.displayName);
  const [description, setDescription] = useState(type.description);
  const [enabled, setEnabled] = useState(type.enabled);
  const [error, setError] = useState<string | null>(null);

  const nameError = !displayName.trim() ? "Enter a name." : displayName.length > 100 ? "Use at most 100 characters." : undefined;
  const descriptionError = !description.trim() ? "Enter a description." : description.length > 1000 ? "Use at most 1000 characters." : undefined;

  const save = () => {
    if (nameError || descriptionError) return;
    setError(null);
    updateType.mutate(
      { type, input: { displayName: displayName.trim(), description: description.trim(), enabled } },
      {
        onSuccess: () => {
          toast.success(`${displayName.trim()} updated`);
          onClose();
        },
        onError: (err) => setError(toApiError(err).message),
      },
    );
  };

  return (
    <Dialog open onOpenChange={(open) => !open && !updateType.isPending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit {type.displayName}</DialogTitle>
          <DialogDescription>Requesters see the name and description in the catalog.</DialogDescription>
        </DialogHeader>
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            save();
          }}
        >
          <FormAlert>{error}</FormAlert>
          <FormField label="Display name" error={nameError}>
            <Input value={displayName} onChange={(event) => setDisplayName(event.target.value)} maxLength={100} />
          </FormField>
          <FormField label="Description" error={descriptionError}>
            <Textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} maxLength={1000} />
          </FormField>
          <div className="flex items-start gap-2.5">
            <Checkbox id="type-enabled" checked={enabled} onCheckedChange={(checked) => setEnabled(checked === true)} className="mt-0.5" />
            <div>
              <Label htmlFor="type-enabled">Enabled</Label>
              <p className="text-xs text-muted-foreground">Disabled types disappear from the catalog and can't be requested.</p>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={updateType.isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={updateType.isPending}>
              {updateType.isPending ? <Loader2 className="animate-spin" aria-hidden /> : null}
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const lines = (text: string) =>
  text
    .split(/[\n,]/)
    .map((line) => line.trim())
    .filter(Boolean);

const OU_ID = /^ou-[0-9a-z]{4,32}-[a-z0-9]{8,32}$/;
const DOMAIN = /^[a-z0-9.-]+\.[a-z]{2,}$/;
const FOLDER_ID = /^[0-9]{1,20}$/;

function PolicyDialog({ type, policy, onClose }: { type: AdminResourceTypeResponse; policy: PolicyResponse; onClose: () => void }) {
  const { updatePolicy } = useCatalogAdminMutations();
  const c = parseConstraints(policy.constraints);
  const [allowed, setAllowed] = useState(policy.allowed);
  const [limit, setLimit] = useState(policy.maxActivePerUser ? String(policy.maxActivePerUser) : "");
  const [ous, setOus] = useState(c.allowedOrganizationalUnitIds.join("\n"));
  const [domains, setDomains] = useState(c.allowedEmailDomains.join("\n"));
  const [folders, setFolders] = useState(c.allowedFolderIds.join("\n"));
  const [cpu, setCpu] = useState(c.maxCpuCores ? String(c.maxCpuCores) : "");
  const [memory, setMemory] = useState(c.maxMemoryGi ? String(c.maxMemoryGi) : "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const buildConstraints = (): { constraints: JsonObject; problems: Record<string, string> } => {
    const problems: Record<string, string> = {};
    if (type.code === "AWS_ACCOUNT") {
      const ouList = lines(ous);
      const domainList = lines(domains).map((d) => d.toLowerCase().replace(/^@/, ""));
      const badOu = ouList.find((ou) => !OU_ID.test(ou));
      if (badOu) problems.allowedOrganizationalUnitIds = `“${badOu}” is not an organizational unit ID (ou-xxxx-xxxxxxxx).`;
      else if (allowed && ouList.length === 0) problems.allowedOrganizationalUnitIds = "List at least one OU when requests are allowed.";
      const badDomain = domainList.find((d) => !DOMAIN.test(d));
      if (badDomain) problems.allowedEmailDomains = `“${badDomain}” is not a domain name.`;
      return {
        constraints: { allowedOrganizationalUnitIds: ouList, ...(domainList.length ? { allowedEmailDomains: domainList } : {}) },
        problems,
      };
    }
    if (type.code === "GCP_PROJECT") {
      const folderList = lines(folders);
      const bad = folderList.find((f) => !FOLDER_ID.test(f));
      if (bad) problems.allowedFolderIds = `“${bad}” is not a numeric folder ID.`;
      else if (allowed && folderList.length === 0) problems.allowedFolderIds = "List at least one folder when requests are allowed.";
      return { constraints: { allowedFolderIds: folderList }, problems };
    }
    const constraints: JsonObject = {};
    for (const [key, raw, label] of [
      ["maxCpuCores", cpu, "CPU"],
      ["maxMemoryGi", memory, "memory"],
    ] as const) {
      if (raw.trim()) {
        const value = Number(raw);
        if (!(value > 0) || !/^\d+(\.\d{1,3})?$/.test(raw.trim())) problems[key] = "Enter a number greater than zero with at most 3 decimals.";
        else constraints[key] = value;
      } else if (allowed) {
        problems[key] = `Set a maximum ${label} quota when requests are allowed.`;
      }
    }
    return { constraints, problems };
  };

  const save = () => {
    setFormError(null);
    const { constraints, problems } = buildConstraints();
    const limitValue = limit.trim() ? Number(limit) : null;
    if (limitValue !== null && (!Number.isInteger(limitValue) || limitValue <= 0)) problems.maxActivePerUser = "Enter a whole number greater than zero, or leave empty for no limit.";
    setErrors(problems);
    if (Object.keys(problems).length > 0) return;

    updatePolicy.mutate(
      { code: type.code, policy, input: { allowed, maxActivePerUser: limitValue, constraints } },
      {
        onSuccess: () => {
          toast.success(`${type.displayName} · ${environmentLabel(policy.environment)} policy saved`);
          onClose();
        },
        onError: (err) => {
          const apiError = toApiError(err);
          const fieldErrors: Record<string, string> = {};
          for (const violation of apiError.fieldErrors) {
            const key = violation.field.replace(/^constraints\./, "").replace(/\[\d+\]$/, "");
            fieldErrors[key] = violation.message.charAt(0).toUpperCase() + violation.message.slice(1);
          }
          setErrors(fieldErrors);
          setFormError(apiError.fieldErrors.length ? null : apiError.message);
        },
      },
    );
  };

  return (
    <Dialog open onOpenChange={(open) => !open && !updatePolicy.isPending && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {type.displayName} · {environmentLabel(policy.environment)}
          </DialogTitle>
          <DialogDescription>Submissions and approvals are checked against this policy.</DialogDescription>
        </DialogHeader>
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            save();
          }}
        >
          <FormAlert>{formError}</FormAlert>
          <div className="flex items-start gap-2.5">
            <Checkbox id="policy-allowed" checked={allowed} onCheckedChange={(checked) => setAllowed(checked === true)} className="mt-0.5" />
            <div>
              <Label htmlFor="policy-allowed">Allow requests in {environmentLabel(policy.environment).toLowerCase()}</Label>
              <p className="text-xs text-muted-foreground">An allowing policy must restrict where resources are placed.</p>
            </div>
          </div>
          <FormField label="Active requests per person" optional error={errors.maxActivePerUser} hint="Pending, approved and provisioned requests count. Empty means no limit.">
            <Input inputMode="numeric" value={limit} onChange={(event) => setLimit(event.target.value)} />
          </FormField>

          {type.code === "AWS_ACCOUNT" ? (
            <>
              <FormField label="Approved organizational units" error={errors.allowedOrganizationalUnitIds} hint="One OU ID per line, e.g. ou-ab12-cd34ef56.">
                <Textarea rows={4} className="font-mono text-xs" value={ous} onChange={(event) => setOus(event.target.value)} spellCheck={false} />
              </FormField>
              <FormField label="Approved email domains" optional error={errors.allowedEmailDomains} hint="One per line. Empty allows any domain.">
                <Textarea rows={2} className="font-mono text-xs" value={domains} onChange={(event) => setDomains(event.target.value)} spellCheck={false} />
              </FormField>
            </>
          ) : null}
          {type.code === "GCP_PROJECT" ? (
            <FormField label="Approved folders" error={errors.allowedFolderIds} hint="One numeric folder ID per line.">
              <Textarea rows={4} className="font-mono text-xs" value={folders} onChange={(event) => setFolders(event.target.value)} spellCheck={false} />
            </FormField>
          ) : null}
          {type.code === "K8S_NAMESPACE" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField label="Max CPU (cores)" error={errors.maxCpuCores}>
                <Input inputMode="decimal" value={cpu} onChange={(event) => setCpu(event.target.value)} />
              </FormField>
              <FormField label="Max memory (GiB)" error={errors.maxMemoryGi}>
                <Input inputMode="decimal" value={memory} onChange={(event) => setMemory(event.target.value)} />
              </FormField>
            </div>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={updatePolicy.isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={updatePolicy.isPending}>
              {updatePolicy.isPending ? <Loader2 className="animate-spin" aria-hidden /> : null}
              Save policy
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
