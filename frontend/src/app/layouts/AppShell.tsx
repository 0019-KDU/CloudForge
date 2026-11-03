import { Menu, PanelLeft } from "lucide-react";
import { Suspense, useState } from "react";
import { Outlet, useLocation } from "react-router";

import { PageSkeleton } from "@/components/feedback/Loaders";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "@/components/ui/sheet";
import { NotificationsMenu } from "@/features/notifications/NotificationsMenu";
import { cn } from "@/lib/utils/cn";

import { Breadcrumbs } from "./Breadcrumbs";
import { SidebarNav } from "./Sidebar";
import { UserMenu } from "./UserMenu";

const COLLAPSED_KEY = "cf-sidebar-collapsed";

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === "true";
  } catch {
    return false;
  }
}

export function AppShell() {
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  const toggleCollapsed = () => {
    setCollapsed((value) => {
      try {
        localStorage.setItem(COLLAPSED_KEY, String(!value));
      } catch {
        // Not persisted when storage is unavailable.
      }
      return !value;
    });
  };

  return (
    <div className="flex min-h-svh">
      <a
        href="#main"
        className="sr-only z-50 rounded-md bg-background px-3 py-2 text-sm focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:ring-2 focus:ring-ring"
      >
        Skip to content
      </a>

      <aside
        className={cn(
          "sticky top-0 hidden h-svh shrink-0 border-r bg-sidebar transition-[width] duration-200 md:block",
          collapsed ? "w-14" : "w-60",
        )}
      >
        <SidebarNav collapsed={collapsed} />
      </aside>

      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="left" className="w-64 bg-sidebar p-0">
          <SheetTitle className="sr-only">Navigation</SheetTitle>
          <SheetDescription className="sr-only">Main navigation</SheetDescription>
          <SidebarNav onNavigate={() => setMobileOpen(false)} />
        </SheetContent>
      </Sheet>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/80 sm:px-6">
          <Button
            variant="ghost"
            size="icon-sm"
            className="md:hidden"
            aria-label="Open navigation"
            onClick={() => setMobileOpen(true)}
          >
            <Menu />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            className="hidden md:inline-flex"
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            aria-pressed={collapsed}
            onClick={toggleCollapsed}
          >
            <PanelLeft />
          </Button>
          <Separator orientation="vertical" className="mx-1 hidden h-5 md:block" />
          <div className="min-w-0 flex-1">
            <Breadcrumbs />
          </div>
          <div className="flex items-center gap-1">
            <NotificationsMenu />
            <UserMenu />
          </div>
        </header>

        <main id="main" tabIndex={-1} className="flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8">
          <div className="mx-auto w-full max-w-7xl">
            {/* Keyed by path so a failed lazy page does not keep its fallback for the next route. */}
            <Suspense key={location.pathname} fallback={<PageSkeleton />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </div>
    </div>
  );
}
