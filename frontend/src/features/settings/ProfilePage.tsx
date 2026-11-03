import { useTheme, type ThemePreference } from "@/app/providers/theme-context";
import { PageHeader } from "@/components/shared/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { useCurrentUser } from "@/features/auth/auth-context";
import { useDirectory } from "@/features/users/directory";
import { formatDate } from "@/lib/utils/format";
import { roleLabel } from "@/lib/utils/labels";

import { ChangePasswordForm } from "./ChangePasswordForm";
import { DisplayNameForm } from "./DisplayNameForm";

export function ProfilePage() {
  const user = useCurrentUser();
  const { entries, isLoading } = useDirectory([user.managerId]);
  const manager = user.managerId ? entries.get(user.managerId) : undefined;

  return (
    <div className="space-y-6">
      <PageHeader title="Profile" description="Your account details, password and appearance." />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Account</CardTitle>
              <CardDescription>Roles, manager and team are managed by platform administrators.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <dl className="grid gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Email</dt>
                  <dd className="mt-0.5 font-medium break-all">{user.email}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Roles</dt>
                  <dd className="mt-1 flex flex-wrap gap-1.5">
                    {user.roles.map((role) => (
                      <Badge key={role} variant="secondary">
                        {roleLabel(role)}
                      </Badge>
                    ))}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Manager (approves your requests)</dt>
                  <dd className="mt-0.5 font-medium">
                    {!user.managerId ? (
                      <span className="font-normal text-muted-foreground">
                        None assigned. You can't submit requests until an administrator assigns one.
                      </span>
                    ) : isLoading ? (
                      <Skeleton className="h-5 w-40" />
                    ) : manager ? (
                      <>
                        {manager.displayName} <span className="font-normal text-muted-foreground">· {manager.email}</span>
                      </>
                    ) : (
                      <span className="font-normal text-muted-foreground">Unavailable</span>
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Member since</dt>
                  <dd className="mt-0.5 font-medium">{formatDate(user.createdAt)}</dd>
                </div>
              </dl>
              <DisplayNameForm />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Password</CardTitle>
              <CardDescription>Changing your password signs you out on every device, including this one.</CardDescription>
            </CardHeader>
            <CardContent>
              <ChangePasswordForm />
            </CardContent>
          </Card>
        </div>

        <AppearanceCard />
      </div>
    </div>
  );
}

function AppearanceCard() {
  const { theme, setTheme } = useTheme();
  const options: { value: ThemePreference; label: string }[] = [
    { value: "light", label: "Light" },
    { value: "dark", label: "Dark" },
    { value: "system", label: "Match system" },
  ];
  return (
    <Card className="h-fit">
      <CardHeader>
        <CardTitle className="text-base">Appearance</CardTitle>
        <CardDescription>Saved in this browser.</CardDescription>
      </CardHeader>
      <CardContent>
        <RadioGroup
          value={theme}
          onValueChange={(value) => setTheme(value as ThemePreference)}
          aria-label="Theme"
          className="gap-3"
        >
          {options.map((option) => (
            <div key={option.value} className="flex items-center gap-2">
              <RadioGroupItem value={option.value} id={`theme-${option.value}`} />
              <Label htmlFor={`theme-${option.value}`} className="font-normal">
                {option.label}
              </Label>
            </div>
          ))}
        </RadioGroup>
      </CardContent>
    </Card>
  );
}
