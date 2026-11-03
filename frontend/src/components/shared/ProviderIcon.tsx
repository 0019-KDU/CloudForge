import { Box, Cloud, Server } from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { providerLabel, providerOf, resourceTypeLabel } from "@/lib/utils/labels";
import type { CloudProvider, ResourceTypeCode } from "@/types/api";

/** Neutral glyphs, not vendor logos: CloudForge doesn't reuse provider branding. */
export function ProviderIcon({ provider, className }: { provider: CloudProvider; className?: string }) {
  const Icon = provider === "KUBERNETES" ? Box : provider === "GCP" ? Server : Cloud;
  return (
    <span
      className={cn(
        "inline-flex size-7 shrink-0 items-center justify-center rounded-md border bg-muted text-muted-foreground",
        className,
      )}
      aria-hidden
    >
      <Icon className="size-3.5" />
    </span>
  );
}

/** Icon + "AWS account" style label used in tables. */
export function ResourceTypeLabel({ code, withProvider = false }: { code: ResourceTypeCode; withProvider?: boolean }) {
  const provider = providerOf(code);
  return (
    <span className="inline-flex min-w-0 items-center gap-2">
      <ProviderIcon provider={provider} className="size-6" />
      <span className="min-w-0">
        <span className="block truncate">{resourceTypeLabel(code)}</span>
        {withProvider ? <span className="block text-xs text-muted-foreground">{providerLabel(provider)}</span> : null}
      </span>
    </span>
  );
}
