import { KeyRound, Loader2, UserCheck, UserX } from "lucide-react";
import { useState, type ReactNode } from "react";
import { useParams } from "react-router";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { FormAlert } from "@/components/shared/FormAlert";
import { KeyValueList } from "@/components/shared/KeyValueList";
import { PageHeader } from "@/components/shared/PageHeader";
import { ToneBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useCurrentUser } from "@/features/auth/auth-context";
import { toApiError } from "@/lib/api/problem";
import type { PasswordResetResponse, Role, UserResponse } from "@/types/api";

import { adminApi, useAdminUser, useDepartments, useInvalidateUsers, useManagerCandidates, useTeams } from "../api";
import { OneTimeSecret } from "../components/OneTimeSecret";
import { RoleCheckboxes } from "../components/RoleCheckboxes";

const NONE = "__none__";

export function UserDetailPage() {
  const { id = "" } = useParams();
  const query = useAdminUser(id);
  if (query.isLoading) return <PageSkeleton />;
  if (query.error) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  if (!query.data) return null;
  return <UserDetail user={query.data} />;
}

/** Turns stale-version and business errors into a message; refetches when someone else changed the user. */
function useSaver(user: UserResponse) {
  const invalidate = useInvalidateUsers();
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = async (key: string, action: () => Promise<UserResponse>, success: string) => {
    setPending(key);
    setError(null);
    try {
      invalidate(await action());
      toast.success(success);
      return true;
    } catch (err) {
      const apiError = toApiError(err);
      if (apiError.kind === "conflict") invalidate();
      setError(apiError.message);
      return false;
    } finally {
      setPending(null);
    }
  };
  return { run, pending, error, setError, user };
}

function UserDetail({ user }: { user: UserResponse }) {
  const me = useCurrentUser();
  const saver = useSaver(user);
  const self = me.id === user.id;

  return (
    <div className="space-y-6">
      <PageHeader
        title={user.displayName}
        description={user.email}
        eyebrow={user.status === "ACTIVE" ? <ToneBadge tone="success" label="Active" /> : <ToneBadge tone="neutral" label="Disabled" />}
        actions={<AccountActions user={user} self={self} saver={saver} />}
      />
      <FormAlert>{saver.error}</FormAlert>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <RolesCard key={user.version} user={user} self={self} saver={saver} />
          <OrganizationCard user={user} saver={saver} />
        </div>
        <Card className="h-fit">
          <CardHeader>
            <CardTitle className="text-sm font-semibold">
              <h2>Account</h2>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <KeyValueList
              columns={1}
              items={[
                { label: "User ID", value: <code className="font-mono text-xs break-all">{user.id}</code> },
                { label: "Joined", value: <Timestamp value={user.createdAt} /> },
                { label: "Last changed", value: <Timestamp value={user.updatedAt} /> },
              ]}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

type Saver = ReturnType<typeof useSaver>;

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">
          <h2>{title}</h2>
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

function RolesCard({ user, self, saver }: { user: UserResponse; self: boolean; saver: Saver }) {
  const [roles, setRoles] = useState<Role[]>(user.roles);
  const sameAsSaved = roles.length === user.roles.length && roles.every((role) => user.roles.includes(role));
  const losingAdmin = self && user.roles.includes("PLATFORM_ADMIN") && !roles.includes("PLATFORM_ADMIN");

  return (
    <Section
      title="Roles"
      description="Changing roles signs the user out of other sessions. New roles apply within 10 minutes on already-open sessions."
    >
      <div className="space-y-4">
        <RoleCheckboxes idPrefix="user-roles" value={roles} onChange={setRoles} disabled={saver.pending !== null} />
        {losingAdmin ? <p className="text-xs text-destructive">You can't remove your own platform admin role.</p> : null}
        <div className="flex gap-2">
          <Button
            onClick={() => void saver.run("roles", () => adminApi.replaceRoles(user, roles), "Roles updated")}
            disabled={sameAsSaved || roles.length === 0 || losingAdmin || saver.pending !== null}
          >
            {saver.pending === "roles" ? <Loader2 className="animate-spin" aria-hidden /> : null}
            Save roles
          </Button>
          {!sameAsSaved ? (
            <Button variant="ghost" onClick={() => setRoles(user.roles)}>
              Reset
            </Button>
          ) : null}
        </div>
      </div>
    </Section>
  );
}

function OrganizationCard({ user, saver }: { user: UserResponse; saver: Saver }) {
  const managers = useManagerCandidates();
  const teams = useTeams();
  const departments = useDepartments();
  const departmentName = new Map(departments.data?.map((d) => [d.id, d.name]));
  const managerOptions = (managers.data ?? []).filter((candidate) => candidate.id !== user.id);
  const currentManagerMissing = user.managerId && !managerOptions.some((m) => m.id === user.managerId);

  return (
    <Section
      title="Organization"
      description="The manager approves this user's requests. Pending requests keep the approver they were submitted to."
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <label htmlFor="user-manager" className="text-sm font-medium">
            Manager
          </label>
          <Select
            value={user.managerId ?? NONE}
            onValueChange={(value) =>
              void saver.run("manager", () => adminApi.assignManager(user, value === NONE ? null : value), "Manager updated")
            }
            disabled={saver.pending !== null || managers.isLoading}
          >
            <SelectTrigger id="user-manager" className="w-full">
              <SelectValue placeholder={managers.isLoading ? "Loading…" : "No manager"} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>No manager</SelectItem>
              {currentManagerMissing && user.managerId ? (
                <SelectItem value={user.managerId}>Current manager (no longer eligible)</SelectItem>
              ) : null}
              {managerOptions.map((manager) => (
                <SelectItem key={manager.id} value={manager.id}>
                  {manager.displayName} · {manager.email}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">Only active users with the Manager role can be chosen.</p>
        </div>
        <div className="space-y-1.5">
          <label htmlFor="user-team" className="text-sm font-medium">
            Team
          </label>
          <Select
            value={user.teamId ?? NONE}
            onValueChange={(value) => void saver.run("team", () => adminApi.assignTeam(user, value === NONE ? null : value), "Team updated")}
            disabled={saver.pending !== null || teams.isLoading}
          >
            <SelectTrigger id="user-team" className="w-full">
              <SelectValue placeholder="No team" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>No team</SelectItem>
              {(teams.data ?? []).map((team) => (
                <SelectItem key={team.id} value={team.id}>
                  {team.name}
                  {departmentName.get(team.departmentId) ? ` · ${departmentName.get(team.departmentId) ?? ""}` : ""}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
    </Section>
  );
}

function AccountActions({ user, self, saver }: { user: UserResponse; self: boolean; saver: Saver }) {
  const [confirm, setConfirm] = useState<"disable" | "enable" | "reset" | null>(null);
  const [issued, setIssued] = useState<PasswordResetResponse | null>(null);
  const [resetPending, setResetPending] = useState(false);

  const issueReset = async () => {
    setResetPending(true);
    saver.setError(null);
    try {
      setIssued(await adminApi.issuePasswordReset(user.id));
      setConfirm(null);
    } catch (err) {
      saver.setError(toApiError(err).message);
      setConfirm(null);
    } finally {
      setResetPending(false);
    }
  };

  return (
    <>
      {user.status === "ACTIVE" ? (
        <>
          <Button variant="outline" onClick={() => setConfirm("reset")}>
            <KeyRound aria-hidden /> Reset password
          </Button>
          {!self ? (
            <Button variant="outline" onClick={() => setConfirm("disable")}>
              <UserX aria-hidden /> Disable
            </Button>
          ) : null}
        </>
      ) : (
        <Button variant="outline" onClick={() => setConfirm("enable")}>
          <UserCheck aria-hidden /> Enable
        </Button>
      )}

      <ConfirmDialog
        open={confirm === "disable"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title={`Disable ${user.displayName}?`}
        description="They are signed out and can't sign in or change anything. Their requests and history stay. A manager with reports must have them reassigned first."
        confirmLabel="Disable user"
        tone="destructive"
        pending={saver.pending === "disable"}
        onConfirm={() => void saver.run("disable", () => adminApi.disable(user), "User disabled").then(() => setConfirm(null))}
      />
      <ConfirmDialog
        open={confirm === "enable"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title={`Enable ${user.displayName}?`}
        description="They can sign in again with their existing password."
        confirmLabel="Enable user"
        pending={saver.pending === "enable"}
        onConfirm={() => void saver.run("enable", () => adminApi.enable(user), "User enabled").then(() => setConfirm(null))}
      />
      <ConfirmDialog
        open={confirm === "reset"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title={`Issue a password reset for ${user.displayName}?`}
        description="Verify the person's identity first. You get a one-time link valid for 24 hours; any earlier reset link stops working. Their current password keeps working until they use it."
        confirmLabel="Create reset link"
        pending={resetPending}
        onConfirm={() => void issueReset()}
      />

      <Dialog open={issued !== null} onOpenChange={(open) => !open && setIssued(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Password reset link</DialogTitle>
            <DialogDescription>For {user.email}</DialogDescription>
          </DialogHeader>
          {issued ? <OneTimeSecret path="/reset-password" token={issued.token} expiresAt={issued.expiresAt} /> : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
