import { Cloud } from "lucide-react";
import { NavLink } from "react-router";

import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useCurrentUser } from "@/features/auth/auth-context";
import { cn } from "@/lib/utils/cn";

import { navigationFor } from "../router/navigation";

interface SidebarProps {
  collapsed?: boolean;
  onNavigate?: () => void;
}

export function SidebarNav({ collapsed = false, onNavigate }: SidebarProps) {
  const user = useCurrentUser();
  const sections = navigationFor(user);

  return (
    <div className="flex h-full flex-col">
      <div className={cn("flex h-14 shrink-0 items-center gap-2 px-4", collapsed && "justify-center px-0")}>
        <div className="flex size-7 items-center justify-center rounded-md bg-primary text-primary-foreground">
          <Cloud className="size-4" aria-hidden />
        </div>
        {collapsed ? null : <span className="text-sm font-semibold tracking-tight">CloudForge</span>}
      </div>

      <nav aria-label="Main" className="flex-1 space-y-5 overflow-y-auto px-3 py-3">
        {sections.map((section, index) => (
          <div key={section.label ?? index}>
            {section.label && !collapsed ? (
              <div className="mb-1 px-2 text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
                {section.label}
              </div>
            ) : null}
            <ul className="space-y-0.5">
              {section.items.map((item) => {
                const link = (
                  <NavLink
                    to={item.to}
                    end={item.end ?? false}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        "flex h-8 items-center gap-2.5 rounded-md px-2 text-sm text-sidebar-foreground transition-colors",
                        "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                        isActive && "bg-sidebar-accent font-medium text-sidebar-accent-foreground",
                        collapsed && "justify-center px-0",
                      )
                    }
                  >
                    <item.icon className="size-4 shrink-0" aria-hidden />
                    {collapsed ? <span className="sr-only">{item.label}</span> : <span className="truncate">{item.label}</span>}
                  </NavLink>
                );
                return (
                  <li key={item.to}>
                    {collapsed ? (
                      <Tooltip>
                        <TooltipTrigger asChild>{link}</TooltipTrigger>
                        <TooltipContent side="right">{item.label}</TooltipContent>
                      </Tooltip>
                    ) : (
                      link
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>
    </div>
  );
}
