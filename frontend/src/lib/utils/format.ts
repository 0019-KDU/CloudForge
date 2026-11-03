/** Formatting for backend values. Instants are UTC; they are shown in the viewer's time zone. */

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });
const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });
const fullFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "full", timeStyle: "long" });
const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });

export function formatDate(instant: string) {
  return dateFormat.format(new Date(instant));
}

export function formatDateTime(instant: string) {
  return dateTimeFormat.format(new Date(instant));
}

/** Unambiguous form for tooltips and audit views. */
export function formatFull(instant: string) {
  return fullFormat.format(new Date(instant));
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 24 * 3600],
  ["month", 30 * 24 * 3600],
  ["week", 7 * 24 * 3600],
  ["day", 24 * 3600],
  ["hour", 3600],
  ["minute", 60],
];

export function formatRelative(instant: string, now: number = Date.now()) {
  const seconds = Math.round((new Date(instant).getTime() - now) / 1000);
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return relativeFormat.format(Math.round(seconds / size), unit);
  }
  return relativeFormat.format(0, "second");
}

export function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? "?";
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? "") : "";
  return (first + last).toUpperCase();
}
