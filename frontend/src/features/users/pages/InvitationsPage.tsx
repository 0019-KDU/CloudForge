import { zodResolver } from "@hookform/resolvers/zod";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Ban, Loader2, MailPlus, Mails } from "lucide-react";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { EmptyState } from "@/components/feedback/EmptyState";
import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { PageHeader } from "@/components/shared/PageHeader";
import { ToneBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { DataTable, type Column } from "@/components/tables/DataTable";
import { FilterBar, FilterSelect } from "@/components/tables/FilterBar";
import { TablePagination } from "@/components/tables/TablePagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useListParams } from "@/hooks/useListParams";
import { toApiError } from "@/lib/api/problem";
import { applyServerErrors } from "@/lib/utils/forms";
import { roleLabel } from "@/lib/utils/labels";
import { emailSchema } from "@/lib/validations/common";
import { ROLES, type InvitationResponse, type InvitationStatus, type IssuedInvitationResponse } from "@/types/api";

import { adminApi, adminKeys, useManagerCandidates, useTeams } from "../api";
import { OneTimeSecret } from "../components/OneTimeSecret";
import { RoleCheckboxes } from "../components/RoleCheckboxes";

const FILTERS = ["status"] as const;
const NONE = "__none__";

export function InvitationsPage() {
  const list = useListParams(FILTERS, { sort: { field: "createdAt", direction: "desc" } });
  const status = list.filters.status as InvitationStatus | undefined;
  const params = { status, page: list.page, size: list.size, sort: list.sortParam };
  const query = useQuery({
    queryKey: [...adminKeys.invitations, params],
    queryFn: () => adminApi.invitations(params),
    placeholderData: keepPreviousData,
  });
  const [inviting, setInviting] = useState(false);
  const [revoking, setRevoking] = useState<InvitationResponse | null>(null);

  const columns: Column<InvitationResponse>[] = [
    { id: "email", header: "Email", sortField: "email", cell: (inv) => <span className="font-medium break-all">{inv.email}</span> },
    {
      id: "roles",
      header: "Roles",
      hideBelow: "md",
      cell: (inv) => (
        <div className="flex flex-wrap gap-1">
          {inv.roles.map((role) => (
            <Badge key={role} variant="secondary" className="font-normal">
              {roleLabel(role)}
            </Badge>
          ))}
        </div>
      ),
    },
    { id: "status", header: "Status", sortField: "status", cell: (inv) => <InvitationBadge invitation={inv} /> },
    { id: "expires", header: "Expires", sortField: "expiresAt", hideBelow: "sm", cell: (inv) => <Timestamp value={inv.expiresAt} mode="relative" /> },
    { id: "created", header: "Sent", sortField: "createdAt", hideBelow: "lg", cell: (inv) => <Timestamp value={inv.createdAt} mode="relative" /> },
    {
      id: "actions",
      header: <span className="sr-only">Actions</span>,
      className: "text-right",
      cell: (inv) =>
        inv.status === "PENDING" ? (
          <Button variant="ghost" size="xs" onClick={() => setRevoking(inv)}>
            <Ban aria-hidden /> Revoke
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Invitations"
        description="Accounts are invitation-only. Roles, manager and team are set here and can't be changed by the invitee."
        actions={
          <Button onClick={() => setInviting(true)}>
            <MailPlus aria-hidden /> Invite user
          </Button>
        }
      />
      <Card className="gap-0 overflow-hidden py-0">
        <FilterBar onReset={list.reset} canReset={list.hasFilters}>
          <FilterSelect
            label="Status"
            allLabel="All invitations"
            value={status}
            onChange={(value) => list.setFilter("status", value)}
            options={[
              { value: "PENDING", label: "Pending" },
              { value: "ACCEPTED", label: "Accepted" },
              { value: "REVOKED", label: "Revoked" },
            ]}
          />
        </FilterBar>
        <DataTable
          caption="Invitations"
          columns={columns}
          rows={query.data?.content}
          rowKey={(inv) => inv.id}
          isLoading={query.isLoading}
          isFetching={query.isFetching && !query.isLoading}
          error={query.error}
          onRetry={() => void query.refetch()}
          sort={list.sort}
          onSortChange={list.setSort}
          empty={
            <EmptyState
              icon={Mails}
              title="No invitations"
              description="Invite engineers, managers, admins and auditors to give them access."
              action={
                <Button size="sm" onClick={() => setInviting(true)}>
                  Invite user
                </Button>
              }
            />
          }
        />
        {query.data ? (
          <TablePagination
            page={query.data.page}
            size={query.data.size}
            totalElements={query.data.totalElements}
            totalPages={query.data.totalPages}
            onPageChange={list.setPage}
            onSizeChange={list.setSize}
          />
        ) : null}
      </Card>
      {inviting ? <InviteDialog onClose={() => setInviting(false)} /> : null}
      {revoking ? <RevokeDialog invitation={revoking} onClose={() => setRevoking(null)} /> : null}
    </div>
  );
}

function InvitationBadge({ invitation }: { invitation: InvitationResponse }) {
  if (invitation.status === "ACCEPTED") return <ToneBadge tone="success" label="Accepted" />;
  if (invitation.status === "REVOKED") return <ToneBadge tone="neutral" label="Revoked" />;
  if (invitation.expired) return <ToneBadge tone="neutral" label="Expired" />;
  return <ToneBadge tone="warning" label="Pending" />;
}

const inviteSchema = z.object({
  email: emailSchema,
  roles: z.array(z.enum(ROLES)).min(1, "Choose at least one role."),
  managerId: z.string(),
  teamId: z.string(),
});
type InviteValues = z.infer<typeof inviteSchema>;

function InviteDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const managers = useManagerCandidates();
  const teams = useTeams();
  const [issued, setIssued] = useState<IssuedInvitationResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<InviteValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: "", roles: ["ENGINEER"], managerId: NONE, teamId: NONE },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      const result = await adminApi.invite({
        email: values.email,
        roles: values.roles,
        managerId: values.managerId === NONE ? null : values.managerId,
        teamId: values.teamId === NONE ? null : values.teamId,
      });
      setIssued(result);
      void queryClient.invalidateQueries({ queryKey: adminKeys.invitations });
    } catch (error) {
      setFormError(
        applyServerErrors(error, form.setError, {
          fields: ["email", "roles"],
          codeFields: { EMAIL_ALREADY_REGISTERED: "email", INVITATION_ALREADY_PENDING: "email" },
        }),
      );
    }
  });

  return (
    <Dialog open onOpenChange={(open) => !open && !isSubmitting && onClose()}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{issued ? "Invitation created" : "Invite a user"}</DialogTitle>
          <DialogDescription>
            {issued ? `Send this link to ${issued.invitation.email}.` : "They create their own password when they accept."}
          </DialogDescription>
        </DialogHeader>
        {issued ? (
          <>
            <OneTimeSecret path="/register" token={issued.token} expiresAt={issued.invitation.expiresAt} />
            <DialogFooter>
              <Button onClick={onClose}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <FormAlert>{formError}</FormAlert>
            <FormField label="Email" error={errors.email?.message}>
              <Input type="email" autoComplete="off" autoFocus {...form.register("email")} />
            </FormField>
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">Roles</legend>
              <Controller
                control={form.control}
                name="roles"
                render={({ field }) => <RoleCheckboxes idPrefix="invite-role" value={field.value} onChange={field.onChange} />}
              />
              {errors.roles ? <p className="text-xs font-medium text-destructive">{errors.roles.message}</p> : null}
            </fieldset>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField label="Manager" optional hint="Approves their requests.">
                <Controller
                  control={form.control}
                  name="managerId"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>No manager</SelectItem>
                        {(managers.data ?? []).map((manager) => (
                          <SelectItem key={manager.id} value={manager.id}>
                            {manager.displayName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </FormField>
              <FormField label="Team" optional>
                <Controller
                  control={form.control}
                  name="teamId"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>No team</SelectItem>
                        {(teams.data ?? []).map((team) => (
                          <SelectItem key={team.id} value={team.id}>
                            {team.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </FormField>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? <Loader2 className="animate-spin" aria-hidden /> : null}
                Create invitation
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function RevokeDialog({ invitation, onClose }: { invitation: InvitationResponse; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const revoke = useMutation({
    mutationFn: () => adminApi.revokeInvitation(invitation.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.invitations });
      toast.success(`Invitation for ${invitation.email} revoked`);
      onClose();
    },
    onError: (err) => setError(toApiError(err).message),
  });
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={`Revoke the invitation for ${invitation.email}?`}
      description="The link stops working immediately. You can send a new invitation later."
      confirmLabel="Revoke invitation"
      tone="destructive"
      pending={revoke.isPending}
      onConfirm={() => revoke.mutate()}
    >
      <FormAlert>{error}</FormAlert>
    </ConfirmDialog>
  );
}
