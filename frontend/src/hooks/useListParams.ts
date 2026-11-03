import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router";

export interface SortState {
  field: string;
  direction: "asc" | "desc";
}

export interface ListParams<F extends string> {
  page: number;
  size: number;
  sort: SortState | null;
  filters: Record<F, string | undefined>;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
  setSort: (sort: SortState | null) => void;
  setFilter: (key: F, value: string | undefined) => void;
  reset: () => void;
  /** `field,direction` as Spring expects, or undefined for the endpoint default. */
  sortParam: string | undefined;
  hasFilters: boolean;
}

export const PAGE_SIZES = [10, 20, 50, 100] as const;

/**
 * List state (page, size, sort, filters) kept in the URL, so lists are linkable and survive reloads.
 * Changing a filter or the sort goes back to the first page.
 */
export function useListParams<F extends string>(
  filterKeys: readonly F[],
  defaults: { size?: number; sort?: SortState } = {},
): ListParams<F> {
  const [params, setParams] = useSearchParams();
  const defaultSize = defaults.size ?? 20;

  const page = Math.max(0, Number(params.get("page") ?? "1") - 1) || 0;
  const sizeParam = Number(params.get("size"));
  const size = (PAGE_SIZES as readonly number[]).includes(sizeParam) ? sizeParam : defaultSize;
  const sort = parseSort(params.get("sort")) ?? defaults.sort ?? null;

  const filterSignature = filterKeys.map((key) => params.get(key) ?? "").join("|");
  const filters = useMemo(() => {
    const result = {} as Record<F, string | undefined>;
    for (const key of filterKeys) result[key] = params.get(key) ?? undefined;
    return result;
    // eslint-disable-next-line react-hooks/exhaustive-deps -- recomputed from the URL values
  }, [filterSignature]);

  const update = useCallback(
    (mutate: (next: URLSearchParams) => void) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          mutate(next);
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const setPage = useCallback(
    (value: number) => update((next) => (value <= 0 ? next.delete("page") : next.set("page", String(value + 1)))),
    [update],
  );
  const setSize = useCallback(
    (value: number) =>
      update((next) => {
        next.delete("page");
        if (value === defaultSize) next.delete("size");
        else next.set("size", String(value));
      }),
    [update, defaultSize],
  );
  const setSort = useCallback(
    (value: SortState | null) =>
      update((next) => {
        next.delete("page");
        if (value) next.set("sort", `${value.field},${value.direction}`);
        else next.delete("sort");
      }),
    [update],
  );
  const setFilter = useCallback(
    (key: F, value: string | undefined) =>
      update((next) => {
        next.delete("page");
        if (value) next.set(key, value);
        else next.delete(key);
      }),
    [update],
  );
  const reset = useCallback(
    () =>
      update((next) => {
        for (const key of [...filterKeys, "page", "sort"]) next.delete(key);
      }),
    [update, filterKeys],
  );

  return {
    page,
    size,
    sort,
    filters,
    setPage,
    setSize,
    setSort,
    setFilter,
    reset,
    sortParam: sort ? `${sort.field},${sort.direction}` : undefined,
    hasFilters: filterKeys.some((key) => Boolean(params.get(key))),
  };
}

function parseSort(value: string | null): SortState | null {
  if (!value) return null;
  const [field, direction] = value.split(",");
  if (!field || (direction !== "asc" && direction !== "desc")) return null;
  return { field, direction };
}
