import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PAGE_SIZES } from "@/hooks/useListParams";

interface TablePaginationProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
}

export function TablePagination({ page, size, totalElements, totalPages, onPageChange, onSizeChange }: TablePaginationProps) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);

  return (
    <nav aria-label="Pagination" className="flex flex-col gap-3 border-t px-4 py-3 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
      <p aria-live="polite">
        <span className="tabular-nums">
          {from}–{to}
        </span>{" "}
        of <span className="tabular-nums">{totalElements}</span>
      </p>
      <div className="flex items-center gap-3">
        {onSizeChange ? (
          <div className="flex items-center gap-2">
            <span id="page-size-label">Rows per page</span>
            <Select value={String(size)} onValueChange={(value) => onSizeChange(Number(value))}>
              <SelectTrigger size="sm" className="h-8 w-[70px]" aria-labelledby="page-size-label">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZES.map((option) => (
                  <SelectItem key={option} value={String(option)}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : null}
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon-sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0} aria-label="Previous page">
            <ChevronLeft />
          </Button>
          <span className="px-2 tabular-nums">
            Page {page + 1} of {Math.max(totalPages, 1)}
          </span>
          <Button
            variant="outline"
            size="icon-sm"
            onClick={() => onPageChange(page + 1)}
            disabled={page + 1 >= totalPages}
            aria-label="Next page"
          >
            <ChevronRight />
          </Button>
        </div>
      </div>
    </nav>
  );
}
