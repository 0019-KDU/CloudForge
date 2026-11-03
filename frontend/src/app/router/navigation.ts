import {
  Boxes,
  Building2,
  Cog,
  FilePlus2,
  FileStack,
  History,
  Inbox,
  LayoutDashboard,
  Library,
  Mails,
  ScrollText,
  ShieldCheck,
  UserRound,
  Users,
  type LucideIcon,
} from "lucide-react";

import { hasAnyRole } from "@/lib/utils/permissions";
import type { Role, UserResponse } from "@/types/api";

import { ACCESS } from "./access";

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
  /** Visible when the user holds any of these roles; omitted = every signed-in user. */
  roles?: readonly Role[];
  /** Match the path exactly. */
  end?: boolean;
}

export interface NavSection {
  /** Omitted for the top, unlabelled group. */
  label?: string;
  items: NavItem[];
}

/**
 * The sidebar. Role lists come from the same ACCESS table as the route guards; both are UX only,
 * the backend authorizes every call.
 */
export const NAVIGATION: NavSection[] = [
  { items: [{ label: "Overview", to: "/", icon: LayoutDashboard, end: true }] },
  {
    label: "Resource management",
    items: [
      { label: "My requests", to: "/requests", icon: FileStack, roles: ACCESS.requester, end: true },
      { label: "New request", to: "/requests/new", icon: FilePlus2, roles: ACCESS.requester },
      { label: "Resource catalog", to: "/catalog", icon: Library },
    ],
  },
  {
    label: "Approvals",
    items: [
      { label: "Pending approvals", to: "/approvals", icon: Inbox, roles: ACCESS.manager, end: true },
      { label: "Approval history", to: "/approvals/history", icon: History, roles: ACCESS.manager },
    ],
  },
  {
    label: "Operations",
    items: [
      { label: "All requests", to: "/operations/requests", icon: FileStack, roles: ACCESS.oversight },
      { label: "Provisioning jobs", to: "/operations/provisioning", icon: Cog, roles: ACCESS.oversight },
      { label: "Resource inventory", to: "/inventory", icon: Boxes },
    ],
  },
  {
    label: "Governance",
    items: [
      { label: "Audit logs", to: "/audit", icon: ScrollText, roles: ACCESS.oversight },
      { label: "Users", to: "/admin/users", icon: Users, roles: ACCESS.admin },
      { label: "Invitations", to: "/admin/invitations", icon: Mails, roles: ACCESS.admin },
      { label: "Departments & teams", to: "/admin/organization", icon: Building2, roles: ACCESS.admin },
      { label: "Resource policies", to: "/admin/catalog", icon: ShieldCheck, roles: ACCESS.admin },
    ],
  },
  { label: "System", items: [{ label: "Profile", to: "/profile", icon: UserRound }] },
];

export function navigationFor(user: Pick<UserResponse, "id" | "roles">): NavSection[] {
  return NAVIGATION.map((section) => ({
    ...section,
    items: section.items.filter((item) => !item.roles || hasAnyRole(user, item.roles)),
  })).filter((section) => section.items.length > 0);
}
