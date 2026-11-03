import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api/client";
import type { DirectoryEntry, UUID } from "@/types/api";

const MAX_IDS_PER_CALL = 100;

export const directoryApi = {
  lookup: (ids: readonly UUID[]) =>
    api.get<DirectoryEntry[]>("/users/directory", { params: { ids: ids.join(",") } }).then((r) => r.data),
};

/**
 * Display names for the given user IDs, in one batched request per distinct set (chunked at the
 * backend's limit of 100). Unknown IDs are simply missing from the map.
 */
export function useDirectory(ids: readonly (UUID | null | undefined)[]) {
  const unique = [...new Set(ids.filter((id): id is UUID => Boolean(id)))].sort();

  const query = useQuery({
    queryKey: ["directory", unique],
    enabled: unique.length > 0,
    staleTime: 5 * 60_000,
    queryFn: async () => {
      const chunks: UUID[][] = [];
      for (let i = 0; i < unique.length; i += MAX_IDS_PER_CALL) chunks.push(unique.slice(i, i + MAX_IDS_PER_CALL));
      const results = await Promise.all(chunks.map((chunk) => directoryApi.lookup(chunk)));
      return new Map(results.flat().map((entry) => [entry.id, entry]));
    },
  });

  return { entries: query.data ?? new Map<UUID, DirectoryEntry>(), isLoading: query.isLoading };
}
