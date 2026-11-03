import { createBrowserRouter, type RouteObject } from "react-router";

import { RouteErrorBoundary } from "@/components/feedback/RouteErrorBoundary";
import { UnauthorizedPage } from "@/features/auth/pages/UnauthorizedPage";
import type { Role } from "@/types/api";

import { AppShell } from "../layouts/AppShell";
import { AuthLayout } from "../layouts/AuthLayout";
import type { RouteHandle } from "../layouts/Breadcrumbs";
import { ACCESS } from "./access";
import { PublicOnly, RequireAuth, RequireRole } from "./guards";

/** Lazy route module: keeps each page in its own chunk. */
function page<K extends string>(load: () => Promise<Record<K, React.ComponentType>>, name: K): RouteObject["lazy"] {
  return async () => ({ Component: (await load())[name] });
}

const handle = (crumb: RouteHandle["crumb"]): RouteHandle => ({ crumb });

/** Groups child routes behind a role check (UX only; the backend enforces it again). */
const restricted = (roles: readonly Role[], children: RouteObject[]): RouteObject => ({
  element: <RequireRole roles={roles} />,
  children,
});

export const routes: RouteObject[] = [
  {
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <PublicOnly />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              { path: "login", lazy: page(() => import("@/features/auth/pages/LoginPage"), "LoginPage") },
              { path: "register", lazy: page(() => import("@/features/auth/pages/RegisterPage"), "RegisterPage") },
              {
                path: "reset-password",
                lazy: page(() => import("@/features/auth/pages/ResetPasswordPage"), "ResetPasswordPage"),
              },
            ],
          },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            errorElement: <RouteErrorBoundary />,
            handle: handle("Overview"),
            children: [
              { index: true, lazy: page(() => import("@/features/dashboard/OverviewPage"), "OverviewPage") },

              {
                // No crumb here: admins and auditors may open a request but not "My requests".
                path: "requests",
                children: [
                  restricted(ACCESS.requester, [
                    {
                      index: true,
                      handle: handle("My requests"),
                      lazy: page(() => import("@/features/requests/pages/MyRequestsPage"), "MyRequestsPage"),
                    },
                    {
                      path: "new",
                      handle: handle("New request"),
                      lazy: page(() => import("@/features/requests/pages/NewRequestPage"), "NewRequestPage"),
                    },
                  ]),
                  {
                    path: ":id",
                    handle: handle("Request"),
                    children: [
                      { index: true, lazy: page(() => import("@/features/requests/pages/RequestDetailPage"), "RequestDetailPage") },
                      restricted(ACCESS.requester, [
                        {
                          path: "edit",
                          handle: handle("Edit draft"),
                          lazy: page(() => import("@/features/requests/pages/EditDraftPage"), "EditDraftPage"),
                        },
                      ]),
                    ],
                  },
                ],
              },
              { path: "catalog", handle: handle("Resource catalog"), lazy: page(() => import("@/features/resources/CatalogPage"), "CatalogPage") },
              { path: "inventory", handle: handle("Resource inventory"), lazy: page(() => import("@/features/resources/InventoryPage"), "InventoryPage") },

              restricted(ACCESS.manager, [
                {
                  path: "approvals",
                  handle: handle("Approvals"),
                  children: [
                    { index: true, lazy: page(() => import("@/features/approvals/PendingApprovalsPage"), "PendingApprovalsPage") },
                    {
                      path: "history",
                      handle: handle("History"),
                      lazy: page(() => import("@/features/approvals/ApprovalHistoryPage"), "ApprovalHistoryPage"),
                    },
                  ],
                },
              ]),

              restricted(ACCESS.oversight, [
                {
                  path: "operations/requests",
                  handle: handle("All requests"),
                  lazy: page(() => import("@/features/requests/pages/AllRequestsPage"), "AllRequestsPage"),
                },
                {
                  path: "operations/provisioning",
                  handle: handle("Provisioning jobs"),
                  lazy: page(() => import("@/features/provisioning/ProvisioningJobsPage"), "ProvisioningJobsPage"),
                },
                { path: "audit", handle: handle("Audit log"), lazy: page(() => import("@/features/audit/AuditLogPage"), "AuditLogPage") },
              ]),

              restricted(ACCESS.admin, [
                {
                  path: "admin/users",
                  handle: handle("Users"),
                  children: [
                    { index: true, lazy: page(() => import("@/features/users/pages/UsersPage"), "UsersPage") },
                    {
                      path: ":id",
                      handle: handle("User"),
                      lazy: page(() => import("@/features/users/pages/UserDetailPage"), "UserDetailPage"),
                    },
                  ],
                },
                {
                  path: "admin/invitations",
                  handle: handle("Invitations"),
                  lazy: page(() => import("@/features/users/pages/InvitationsPage"), "InvitationsPage"),
                },
                {
                  path: "admin/organization",
                  handle: handle("Departments & teams"),
                  lazy: page(() => import("@/features/users/pages/OrganizationPage"), "OrganizationPage"),
                },
                {
                  path: "admin/catalog",
                  handle: handle("Resource policies"),
                  lazy: page(() => import("@/features/resources/AdminCatalogPage"), "AdminCatalogPage"),
                },
              ]),

              {
                path: "profile",
                handle: handle("Profile"),
                lazy: page(() => import("@/features/settings/ProfilePage"), "ProfilePage"),
              },
              {
                path: "unauthorized",
                handle: handle("Access denied"),
                // Static: the role guard renders it in place too.
                element: <UnauthorizedPage />,
              },
              {
                path: "*",
                handle: handle("Not found"),
                lazy: page(() => import("@/features/auth/pages/NotFoundPage"), "NotFoundPage"),
              },
            ],
          },
        ],
      },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
