import { LogOut, Monitor, Moon, Sun, UserRound } from "lucide-react";
import { Link } from "react-router";
import { toast } from "sonner";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth, useCurrentUser } from "@/features/auth/auth-context";
import { initials } from "@/lib/utils/format";
import { roleLabel } from "@/lib/utils/labels";

import { useTheme, type ThemePreference } from "../providers/theme-context";

export function UserMenu() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const { theme, setTheme } = useTheme();

  const signOut = () => {
    logout().catch(() => toast.error("You were signed out locally, but the server could not be reached."));
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="rounded-full" aria-label={`Account menu for ${user.displayName}`}>
          <Avatar className="size-7">
            <AvatarFallback className="bg-primary/10 text-xs font-medium text-primary">
              {initials(user.displayName)}
            </AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <div className="truncate text-sm font-medium">{user.displayName}</div>
          <div className="truncate text-xs text-muted-foreground">{user.email}</div>
          <div className="mt-1 text-xs text-muted-foreground">{user.roles.map(roleLabel).join(" · ")}</div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/profile">
            <UserRound /> Profile
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">Theme</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={theme} onValueChange={(value) => setTheme(value as ThemePreference)}>
          <DropdownMenuRadioItem value="light">
            <Sun /> Light
          </DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark">
            <Moon /> Dark
          </DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="system">
            <Monitor /> System
          </DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={signOut}>
          <LogOut /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
