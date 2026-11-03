import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatDateTime, formatFull, formatRelative } from "@/lib/utils/format";

interface TimestampProps {
  value: string | null | undefined;
  /** "relative" shows "3 hours ago"; the exact time is always available on hover and to screen readers. */
  mode?: "relative" | "datetime";
  empty?: string;
}

export function Timestamp({ value, mode = "datetime", empty = "—" }: TimestampProps) {
  if (!value) return <span className="text-muted-foreground">{empty}</span>;
  const text = mode === "relative" ? formatRelative(value) : formatDateTime(value);
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <time dateTime={value} className="whitespace-nowrap tabular-nums" tabIndex={0}>
          {text}
        </time>
      </TooltipTrigger>
      <TooltipContent>{formatFull(value)}</TooltipContent>
    </Tooltip>
  );
}
