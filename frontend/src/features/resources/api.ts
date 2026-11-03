import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api/client";
import type { Environment, JsonObject, ResourceTypeCode, ResourceTypeResponse } from "@/types/api";

export const catalogApi = {
  list: () => api.get<ResourceTypeResponse[]>("/resource-types").then((r) => r.data),
  validateSpec: (code: ResourceTypeCode, environment: Environment, spec: JsonObject) =>
    api.post<{ valid: boolean }>(`/resource-types/${code}/spec-validation`, { environment, spec }).then((r) => r.data),
};

export const catalogKeys = {
  all: ["catalog"] as const,
  list: () => [...catalogKeys.all, "list"] as const,
};

/**
 * Enabled resource types with the environments and limits a requester may use. Admins change
 * policies at any time, so pages always re-check on open instead of trusting a cached copy.
 */
export function useCatalog() {
  return useQuery({ queryKey: catalogKeys.list(), queryFn: catalogApi.list, refetchOnMount: "always" });
}
