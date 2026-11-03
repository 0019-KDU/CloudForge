import { Building2, Loader2, Pencil, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/feedback/EmptyState";
import { ErrorState } from "@/components/feedback/ErrorState";
import { PageSkeleton } from "@/components/feedback/Loaders";
import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { toApiError } from "@/lib/api/problem";
import type { DepartmentResponse, TeamResponse } from "@/types/api";

import { useDepartments, useOrganizationMutations, useTeams } from "../api";

type Editing =
  | { kind: "new-department" }
  | { kind: "rename-department"; department: DepartmentResponse }
  | { kind: "new-team"; department: DepartmentResponse }
  | { kind: "rename-team"; team: TeamResponse };

export function OrganizationPage() {
  const departments = useDepartments();
  const teams = useTeams();
  const [editing, setEditing] = useState<Editing | null>(null);

  const content = () => {
    if (departments.isLoading || teams.isLoading) return <PageSkeleton />;
    if (departments.error) return <ErrorState error={departments.error} onRetry={() => void departments.refetch()} />;
    if (teams.error) return <ErrorState error={teams.error} onRetry={() => void teams.refetch()} />;
    if (!departments.data?.length) {
      return (
        <EmptyState
          icon={Building2}
          title="No departments yet"
          description="Departments group teams. Create one, then add its teams."
          action={
            <Button size="sm" onClick={() => setEditing({ kind: "new-department" })}>
              <Plus aria-hidden /> New department
            </Button>
          }
        />
      );
    }
    return (
      <div className="grid gap-4 lg:grid-cols-2">
        {departments.data.map((department) => {
          const departmentTeams = (teams.data ?? []).filter((team) => team.departmentId === department.id);
          return (
            <Card key={department.id}>
              <CardHeader>
                <CardTitle className="text-base">
                  <h2>{department.name}</h2>
                </CardTitle>
                <CardAction className="flex gap-1">
                  <Button variant="ghost" size="icon-sm" aria-label={`Rename ${department.name}`} onClick={() => setEditing({ kind: "rename-department", department })}>
                    <Pencil />
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setEditing({ kind: "new-team", department })}>
                    <Plus aria-hidden /> Team
                  </Button>
                </CardAction>
              </CardHeader>
              <CardContent>
                {departmentTeams.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No teams yet.</p>
                ) : (
                  <ul className="divide-y rounded-md border">
                    {departmentTeams.map((team) => (
                      <li key={team.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                        <span>{team.name}</span>
                        <Button variant="ghost" size="icon-xs" aria-label={`Rename ${team.name}`} onClick={() => setEditing({ kind: "rename-team", team })}>
                          <Pencil />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Departments & teams"
        description="Organizational structure. Assign people to teams from their user page."
        actions={
          <Button onClick={() => setEditing({ kind: "new-department" })}>
            <Plus aria-hidden /> New department
          </Button>
        }
      />
      {content()}
      {editing ? <NameDialog editing={editing} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function NameDialog({ editing, onClose }: { editing: Editing; onClose: () => void }) {
  const m = useOrganizationMutations();
  const initial = editing.kind === "rename-department" ? editing.department.name : editing.kind === "rename-team" ? editing.team.name : "";
  const [name, setName] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const pending = m.createDepartment.isPending || m.renameDepartment.isPending || m.createTeam.isPending || m.renameTeam.isPending;

  const title = {
    "new-department": "New department",
    "rename-department": "Rename department",
    "new-team": "New team",
    "rename-team": "Rename team",
  }[editing.kind];

  const save = async () => {
    const trimmed = name.trim();
    setError(null);
    setFieldError(undefined);
    if (!trimmed) return setFieldError("Enter a name.");
    if (trimmed.length > 100) return setFieldError("Use at most 100 characters.");
    try {
      if (editing.kind === "new-department") await m.createDepartment.mutateAsync(trimmed);
      else if (editing.kind === "rename-department") await m.renameDepartment.mutateAsync({ department: editing.department, name: trimmed });
      else if (editing.kind === "new-team") await m.createTeam.mutateAsync({ departmentId: editing.department.id, name: trimmed });
      else await m.renameTeam.mutateAsync({ team: editing.team, name: trimmed });
      toast.success(`${title.replace("New", "Created").replace("Rename", "Renamed")}: ${trimmed}`);
      onClose();
    } catch (err) {
      const apiError = toApiError(err);
      if (apiError.code.endsWith("NAME_TAKEN")) setFieldError(apiError.message);
      else setError(apiError.message);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            {editing.kind === "new-team" ? `In ${editing.department.name}. Names are unique within a department.` : "Names must be unique."}
          </DialogDescription>
        </DialogHeader>
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <FormAlert>{error}</FormAlert>
          <FormField label="Name" error={fieldError}>
            <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={100} autoFocus />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
              Cancel
            </Button>
            <Button type="submit" disabled={pending || name.trim() === initial}>
              {pending ? <Loader2 className="animate-spin" aria-hidden /> : null}
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
