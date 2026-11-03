import type { ReactNode } from "react";
import { Navigate, Outlet, useLocation, useSearchParams } from "react-router";

import { FullPageLoader } from "@/components/feedback/Loaders";
import { useAuth } from "@/features/auth/auth-context";
import { UnauthorizedPage } from "@/features/auth/pages/UnauthorizedPage";
import { safeNext } from "@/lib/utils/navigation";
import { hasAnyRole } from "@/lib/utils/permissions";
import type { Role } from "@/types/api";

/** Signed-in users only; everyone else goes to the login page and comes back afterwards. */
export function RequireAuth() {
  const { status, signedOutReason } = useAuth();
  const location = useLocation();

  if (status === "loading") return <FullPageLoader />;
  if (status === "anonymous") {
    const next = location.pathname + location.search;
    const params = new URLSearchParams();
    if (next !== "/") params.set("next", next);
    if (signedOutReason) params.set("reason", signedOutReason);
    const query = params.toString();
    return <Navigate to={`/login${query ? `?${query}` : ""}`} replace />;
  }
  return <Outlet />;
}

/**
 * Renders the Unauthorized page in place (keeping the URL) when the user lacks every listed role.
 * Must be nested inside RequireAuth.
 */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children?: ReactNode }) {
  const { user } = useAuth();
  if (!hasAnyRole(user, roles)) return <UnauthorizedPage />;
  return children ?? <Outlet />;
}

/** Login, registration and reset pages: signed-in users are sent on. */
export function PublicOnly() {
  const { status } = useAuth();
  const [params] = useSearchParams();
  if (status === "loading") return <FullPageLoader />;
  if (status === "authenticated") return <Navigate to={safeNext(params)} replace />;
  return <Outlet />;
}
