import { useState } from "react";

/**
 * Reads a one-time token from the URL fragment (`#token=…`) and removes it from the address bar.
 * The fragment is never sent to servers, so the token stays out of access logs and Referer headers.
 */
export function useFragmentToken(): string {
  const [token] = useState(() => {
    const value = new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "";
    if (value) {
      window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
    }
    return value;
  });
  return token;
}
