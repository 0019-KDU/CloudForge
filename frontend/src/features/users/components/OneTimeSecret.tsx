import { KeyRound } from "lucide-react";

import { CopyButton } from "@/components/shared/CopyButton";

/**
 * Shows a link carrying a one-time token (invitation, password reset). The backend returns the token
 * only once and stores just its hash, so this is the only chance to copy it. The token goes in the URL
 * fragment, which browsers never send to servers.
 */
export function OneTimeSecret({ path, token, expiresAt }: { path: string; token: string; expiresAt: string }) {
  const link = `${window.location.origin}${path}#token=${encodeURIComponent(token)}`;
  return (
    <div className="space-y-3">
      <div className="flex items-start gap-2 rounded-md border border-warning/30 bg-warning-subtle px-3 py-2 text-sm">
        <KeyRound className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden />
        <p>
          Copy this link now and send it to the person through a trusted channel. It is shown only once and expires{" "}
          {new Date(expiresAt).toLocaleString()}.
        </p>
      </div>
      <div className="rounded-md border bg-muted p-3">
        <code className="block font-mono text-xs break-all select-all">{link}</code>
      </div>
      <CopyButton value={link} label="Copy link" />
    </div>
  );
}
