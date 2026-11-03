import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import type { ReactNode } from "react";

import { ErrorState } from "@/components/feedback/ErrorState";
import { TableSkeleton } from "@/components/feedback/Loaders";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { SortState } from "@/hooks/useListParams";
import { cn } from "@/lib/utils/cn";

export interface Column<T> {
  id: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  /** Backend sort property; only whitelisted fields may be listed here. */
  sortField?: string;
  className?: string;
  /** Hide on narrow screens to keep the important columns readable. */
  hideBelow?: "sm" | "md" | "lg" | "xl";
}

interface DataTableProps<T> {
  caption: string;
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  isLoading: boolean;
  error?: unknown;
  onRetry?: () => void;
  /** Shown when the request succeeded with no rows. */
  empty: ReactNode;
  sort?: SortState | null;
  onSortChange?: (sort: SortState | null) => void;
  /** True while a new page or filter loads with the previous rows still visible. */
  isFetching?: boolean;
}

const HIDE: Record<NonNullable<Column<unknown>["hideBelow"]>, string> = {
  sm: "hidden sm:table-cell",
  md: "hidden md:table-cell",
  lg: "hidden lg:table-cell",
  xl: "hidden xl:table-cell",
};

/** Server-driven table: sorting and paging happen in the backend, the table only renders. */
export function DataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  isLoading,
  error,
  onRetry,
  empty,
  sort,
  onSortChange,
  isFetching,
}: DataTableProps<T>) {
  if (isLoading) return <TableSkeleton columns={Math.min(columns.length, 6)} />;
  if (error) return <ErrorState error={error} {...(onRetry ? { onRetry } : {})} />;
  if (!rows || rows.length === 0) return <>{empty}</>;

  return (
    <div className={cn("transition-opacity", isFetching && "opacity-60")} aria-busy={isFetching}>
      <Table>
        <caption className="sr-only">{caption}</caption>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            {columns.map((column) => {
              const active = sort && column.sortField && sort.field === column.sortField ? sort.direction : null;
              return (
                <TableHead
                  key={column.id}
                  className={cn("h-10 text-xs font-medium text-muted-foreground", column.hideBelow && HIDE[column.hideBelow], column.className)}
                  aria-sort={active === "asc" ? "ascending" : active === "desc" ? "descending" : undefined}
                >
                  {column.sortField && onSortChange ? (
                    <button
                      type="button"
                      className="-ml-1 inline-flex items-center gap-1 rounded px-1 py-0.5 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                      onClick={() => {
                        const field = column.sortField ?? "";
                        onSortChange(active === "desc" ? { field, direction: "asc" } : { field, direction: "desc" });
                      }}
                    >
                      {column.header}
                      {active === "asc" ? (
                        <ArrowUp className="size-3" aria-hidden />
                      ) : active === "desc" ? (
                        <ArrowDown className="size-3" aria-hidden />
                      ) : (
                        <ChevronsUpDown className="size-3 opacity-50" aria-hidden />
                      )}
                    </button>
                  ) : (
                    column.header
                  )}
                </TableHead>
              );
            })}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={rowKey(row)}>
              {columns.map((column) => (
                <TableCell
                  key={column.id}
                  className={cn("py-2.5 align-middle", column.hideBelow && HIDE[column.hideBelow], column.className)}
                >
                  {column.cell(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
