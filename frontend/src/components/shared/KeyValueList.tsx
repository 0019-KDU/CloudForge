import type { ReactNode } from "react";

import { cn } from "@/lib/utils/cn";
import { humanizeKey } from "@/lib/utils/labels";

export interface KeyValueItem {
  label: ReactNode;
  value: ReactNode;
}

export function KeyValueList({ items, className, columns = 2 }: { items: KeyValueItem[]; className?: string; columns?: 1 | 2 | 3 }) {
  return (
    <dl
      className={cn(
        "grid gap-x-6 gap-y-4 text-sm",
        columns === 2 && "sm:grid-cols-2",
        columns === 3 && "sm:grid-cols-2 lg:grid-cols-3",
        className,
      )}
    >
      {items.map((item, index) => (
        <div key={index} className="min-w-0">
          <dt className="text-xs text-muted-foreground">{item.label}</dt>
          <dd className="mt-1 min-w-0 break-words">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Renders an untrusted JSON value as text (React escapes it), never as HTML. */
export function JsonValue({ value }: { value: unknown }) {
  if (value === null || value === undefined) return <span className="text-muted-foreground">—</span>;
  if (typeof value === "string") return <span className="font-mono text-xs break-all">{value}</span>;
  if (typeof value === "number" || typeof value === "boolean") {
    return <span className="font-mono text-xs">{String(value)}</span>;
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="text-muted-foreground">None</span>;
    return (
      <ul className="space-y-0.5">
        {(value as unknown[]).map((item, index) => (
          <li key={index}>
            <JsonValue value={item} />
          </li>
        ))}
      </ul>
    );
  }
  return <pre className="overflow-x-auto rounded bg-muted p-2 font-mono text-xs">{JSON.stringify(value, null, 2)}</pre>;
}

/** Key/value view of a JSON object, e.g. a spec or audit details. */
export function JsonObjectList({
  value,
  labels = {},
  columns,
}: {
  value: Record<string, unknown> | null | undefined;
  labels?: Record<string, string>;
  columns?: 1 | 2 | 3;
}) {
  const entries = Object.entries(value ?? {});
  if (entries.length === 0) return <p className="text-sm text-muted-foreground">No details.</p>;
  return (
    <KeyValueList
      columns={columns ?? 2}
      items={entries.map(([key, item]) => ({ label: labels[key] ?? humanizeKey(key), value: <JsonValue value={item} /> }))}
    />
  );
}
