import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronDown, ScrollText } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { JsonObjectList } from "@/components/shared/KeyValueList";
import { PageHeader } from "@/components/shared/PageHeader";
import { Timestamp } from "@/components/shared/Timestamp";
import { UserLabel } from "@/components/shared/UserLabel";
import { DataTable, type Column } from "@/components/tables/DataTable";
import { FilterBar, FilterSelect } from "@/components/tables/FilterBar";
import { TablePagination } from "@/components/tables/TablePagination";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDirectory } from "@/features/users/directory";
import { useListParams } from "@/hooks/useListParams";
import { api } from "@/lib/api/client";
import { humanize } from "@/lib/utils/labels";
import {
  AUDIT_ACTIONS,
  AUDIT_ENTITY_TYPES,
  type AuditEntityType,
  type AuditEventResponse,
  type PageResponse,
} from "@/types/api";

import { auditActionLabel } from "./labels";

interface AuditSearch {
  entityType?: string | undefined;
  entityId?: string | undefined;
  actorId?: string | undefined;
  action?: string | undefined;
  from?: string | undefined;
  to?: string | undefined;
  page: number;
  size: number;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FILTERS = ["entityType", "action", "entityId", "actorId", "from", "to"] as const;

/** `YYYY-MM-DD` from a date input → an ISO instant at the start (or end) of that local day. */
function dayBoundary(value: string | undefined, end: boolean): string | undefined {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined;
  const date = new Date(`${value}T00:00:00`);
  if (end) date.setDate(date.getDate() + 1);
  return date.toISOString();
}

/** Read-only, organization-wide audit search (AUDITOR, PLATFORM_ADMIN). Fixed newest-first order. */
export function AuditLogPage() {
  const list = useListParams(FILTERS, { size: 50 });
  const f = list.filters;
  const search: AuditSearch = {
    entityType: AUDIT_ENTITY_TYPES.includes(f.entityType as AuditEntityType) ? f.entityType : undefined,
    action: f.action,
    entityId: f.entityId && UUID.test(f.entityId) ? f.entityId : undefined,
    actorId: f.actorId && UUID.test(f.actorId) ? f.actorId : undefined,
    from: dayBoundary(f.from, false),
    to: dayBoundary(f.to, true),
    page: list.page,
    size: list.size,
  };

  const query = useQuery({
    queryKey: ["audit-events", search],
    queryFn: () => api.get<PageResponse<AuditEventResponse>>("/audit-events", { params: search }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  const rows = query.data?.content;
  const people = useDirectory(rows?.map((event) => event.actorId) ?? []);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const toggle = (id: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const columns: Column<AuditEventResponse>[] = [
    { id: "time", header: "Time", cell: (event) => <Timestamp value={event.occurredAt} /> },
    {
      id: "actor",
      header: "Actor",
      className: "max-w-48",
      cell: (event) => (
        <UserLabel id={event.actorId} entries={people.entries} loading={people.isLoading} system={event.actorType === "SYSTEM"} detailed />
      ),
    },
    { id: "action", header: "Action", cell: (event) => <span className="font-medium">{auditActionLabel(event.action)}</span> },
    { id: "entity", header: "Entity type", cell: (event) => humanize(event.entityType), hideBelow: "md" },
    {
      id: "entityId",
      header: "Entity ID",
      hideBelow: "lg",
      cell: (event) =>
        event.entityType === "RESOURCE_REQUEST" && event.action !== "REQUEST_DELETED" ? (
          <Link to={`/requests/${event.entityId}`} className="font-mono text-xs text-primary underline-offset-4 hover:underline">
            {event.entityId}
          </Link>
        ) : (
          <code className="font-mono text-xs">{event.entityId}</code>
        ),
    },
    {
      id: "details",
      header: "Details",
      cell: (event) => {
        const count = Object.keys(event.details).length;
        if (count === 0 && !event.correlationId) return <span className="text-muted-foreground">—</span>;
        const open = expanded.has(event.id);
        return (
          <div className="space-y-2">
            <Button variant="ghost" size="xs" onClick={() => toggle(event.id)} aria-expanded={open}>
              <ChevronDown className={open ? "rotate-180 transition-transform" : "transition-transform"} aria-hidden />
              {open ? "Hide" : `Show${count ? ` (${count})` : ""}`}
            </Button>
            {open ? (
              <div className="min-w-64 rounded-md border bg-muted/40 p-3">
                <JsonObjectList value={event.details} columns={1} />
                {event.correlationId ? (
                  <p className="mt-3 text-xs text-muted-foreground">
                    Correlation ID: <code className="select-all">{event.correlationId}</code>
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Audit log"
        description="Every business action, newest first. The log is append-only: entries can't be changed or deleted."
      />
      <Card className="gap-0 overflow-hidden py-0">
        <FilterBar onReset={list.reset} canReset={list.hasFilters}>
          <FilterSelect
            label="Entity type"
            allLabel="All entities"
            value={f.entityType}
            onChange={(value) => list.setFilter("entityType", value)}
            options={AUDIT_ENTITY_TYPES.map((value) => ({ value, label: humanize(value) }))}
          />
          <FilterSelect
            label="Action"
            allLabel="All actions"
            value={f.action}
            onChange={(value) => list.setFilter("action", value)}
            options={AUDIT_ACTIONS.map((value) => ({ value, label: auditActionLabel(value) }))}
            className="sm:w-56"
          />
          <IdFilter label="Entity ID" value={f.entityId} onChange={(value) => list.setFilter("entityId", value)} />
          <IdFilter label="Actor ID" value={f.actorId} onChange={(value) => list.setFilter("actorId", value)} />
          <DateFilter label="From" value={f.from} onChange={(value) => list.setFilter("from", value)} />
          <DateFilter label="To" value={f.to} onChange={(value) => list.setFilter("to", value)} />
        </FilterBar>
        <DataTable
          caption="Audit events"
          columns={columns}
          rows={rows}
          rowKey={(event) => event.id}
          isLoading={query.isLoading}
          isFetching={query.isFetching && !query.isLoading}
          error={query.error}
          onRetry={() => void query.refetch()}
          empty={
            <EmptyState
              icon={ScrollText}
              title={list.hasFilters ? "No matching events" : "No audit events yet"}
              description={list.hasFilters ? "Widen the date range or clear the filters." : "Events are recorded as people use CloudForge."}
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

/** Applies on blur or Enter, and only valid UUIDs, so partial input never hits the API. */
function IdFilter({ label, value, onChange }: { label: string; value: string | undefined; onChange: (value: string | undefined) => void }) {
  const [text, setText] = useState(value ?? "");
  const invalid = text !== "" && !UUID.test(text.trim());
  const apply = () => onChange(text.trim() && !invalid ? text.trim() : undefined);
  return (
    <div className="w-full sm:w-72">
      <Input
        aria-label={label}
        placeholder={label}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onBlur={apply}
        onKeyDown={(event) => event.key === "Enter" && apply()}
        aria-invalid={invalid || undefined}
        className="h-8 font-mono text-xs"
        spellCheck={false}
      />
    </div>
  );
}

function DateFilter({ label, value, onChange }: { label: string; value: string | undefined; onChange: (value: string | undefined) => void }) {
  const id = `audit-${label.toLowerCase()}`;
  return (
    <div className="flex items-center gap-2">
      <Label htmlFor={id} className="text-xs font-normal text-muted-foreground">
        {label}
      </Label>
      <Input id={id} type="date" value={value ?? ""} onChange={(event) => onChange(event.target.value || undefined)} className="h-8 w-40" />
    </div>
  );
}
