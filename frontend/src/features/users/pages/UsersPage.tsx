import { MailPlus, Users } from "lucide-react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { ToneBadge } from "@/components/shared/StatusBadge";
import { Timestamp } from "@/components/shared/Timestamp";
import { DataTable, type Column } from "@/components/tables/DataTable";
import { FilterBar, FilterSelect } from "@/components/tables/FilterBar";
import { TablePagination } from "@/components/tables/TablePagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useListParams } from "@/hooks/useListParams";
import { roleLabel } from "@/lib/utils/labels";
import type { UserResponse } from "@/types/api";

import { useManagerCandidates, useTeams, useUsers } from "../api";

const FILTERS = ["status", "teamId", "managerId"] as const;

export function UsersPage() {
  const list = useListParams(FILTERS, { sort: { field: "displayName", direction: "asc" } });
  const { status, teamId, managerId } = list.filters;
  const query = useUsers({
    status: status === "ACTIVE" || status === "DISABLED" ? status : undefined,
    teamId,
    managerId,
    page: list.page,
    size: list.size,
    sort: list.sortParam,
  });
  const teams = useTeams();
  const managers = useManagerCandidates();
  const teamName = new Map(teams.data?.map((team) => [team.id, team.name]));
  const managerName = new Map(managers.data?.map((user) => [user.id, user.displayName]));

  const columns: Column<UserResponse>[] = [
    {
      id: "name",
      header: "Name",
      sortField: "displayName",
      cell: (user) => (
        <Link to={`/admin/users/${user.id}`} className="block min-w-0 underline-offset-4 hover:underline">
          <span className="block truncate font-medium text-primary">{user.displayName}</span>
          <span className="block truncate text-xs text-muted-foreground">{user.email}</span>
        </Link>
      ),
    },
    {
      id: "roles",
      header: "Roles",
      cell: (user) => (
        <div className="flex flex-wrap gap-1">
          {user.roles.map((role) => (
            <Badge key={role} variant="secondary" className="font-normal">
              {roleLabel(role)}
            </Badge>
          ))}
        </div>
      ),
    },
    {
      id: "status",
      header: "Status",
      sortField: "status",
      cell: (user) =>
        user.status === "ACTIVE" ? <ToneBadge tone="success" label="Active" /> : <ToneBadge tone="neutral" label="Disabled" />,
    },
    {
      id: "manager",
      header: "Manager",
      hideBelow: "lg",
      cell: (user) =>
        user.managerId ? (managerName.get(user.managerId) ?? <span className="text-muted-foreground">Unknown</span>) : <span className="text-muted-foreground">—</span>,
    },
    {
      id: "team",
      header: "Team",
      hideBelow: "md",
      cell: (user) => (user.teamId ? (teamName.get(user.teamId) ?? "—") : <span className="text-muted-foreground">—</span>),
    },
    { id: "created", header: "Joined", sortField: "createdAt", hideBelow: "xl", cell: (user) => <Timestamp value={user.createdAt} mode="relative" /> },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Users"
        description="Roles, managers and teams. Managers approve their reports' requests."
        actions={
          <Button asChild>
            <Link to="/admin/invitations">
              <MailPlus aria-hidden /> Invite users
            </Link>
          </Button>
        }
      />
      <Card className="gap-0 overflow-hidden py-0">
        <FilterBar onReset={list.reset} canReset={list.hasFilters}>
          <FilterSelect
            label="Status"
            allLabel="All statuses"
            value={status}
            onChange={(value) => list.setFilter("status", value)}
            options={[
              { value: "ACTIVE", label: "Active" },
              { value: "DISABLED", label: "Disabled" },
            ]}
          />
          <FilterSelect
            label="Team"
            allLabel="All teams"
            value={teamId}
            onChange={(value) => list.setFilter("teamId", value)}
            options={(teams.data ?? []).map((team) => ({ value: team.id, label: team.name }))}
          />
          <FilterSelect
            label="Manager"
            allLabel="Any manager"
            value={managerId}
            onChange={(value) => list.setFilter("managerId", value)}
            options={(managers.data ?? []).map((user) => ({ value: user.id, label: user.displayName }))}
            className="sm:w-52"
          />
        </FilterBar>
        <DataTable
          caption="Users"
          columns={columns}
          rows={query.data?.content}
          rowKey={(user) => user.id}
          isLoading={query.isLoading}
          isFetching={query.isFetching && !query.isLoading}
          error={query.error}
          onRetry={() => void query.refetch()}
          sort={list.sort}
          onSortChange={list.setSort}
          empty={
            <EmptyState
              icon={Users}
              title={list.hasFilters ? "No matching users" : "No users yet"}
              description={list.hasFilters ? "Clear the filters to see everyone." : "Invite people to give them access to CloudForge."}
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
    </div>
  );
}
