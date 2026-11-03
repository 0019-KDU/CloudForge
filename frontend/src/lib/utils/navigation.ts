/** The `?next=` target after sign-in. Only same-app paths, so it cannot redirect to another site. */
export function safeNext(params: URLSearchParams): string {
  const next = params.get("next");
  return next?.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : "/";
}
