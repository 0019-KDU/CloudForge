import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api/client";
import type { AdminResourceTypeResponse, JsonObject, PolicyResponse, ResourceTypeCode } from "@/types/api";

import { catalogKeys } from "./api";

const ifMatch = (version: number | null) => (version === null ? {} : { headers: { "If-Match": `"${version}"` } });

export interface PolicyInput {
  allowed: boolean;
  maxActivePerUser: number | null;
  constraints: JsonObject;
}

export const catalogAdminApi = {
  list: () => api.get<AdminResourceTypeResponse[]>("/admin/resource-types").then((r) => r.data),
  updateType: (type: AdminResourceTypeResponse, input: { displayName: string; description: string; enabled: boolean }) =>
    api.put<AdminResourceTypeResponse>(`/admin/resource-types/${type.code}`, input, ifMatch(type.version)).then((r) => r.data),
  /** If-Match is the policy's own version; a policy never stored yet has none. */
  updatePolicy: (code: ResourceTypeCode, policy: PolicyResponse, input: PolicyInput) =>
    api
      .put<PolicyResponse>(`/admin/resource-types/${code}/policies/${policy.environment}`, input, ifMatch(policy.version))
      .then((r) => r.data),
};

export const catalogAdminKeys = { all: ["admin", "resource-types"] as const };

export function useAdminCatalog() {
  return useQuery({ queryKey: catalogAdminKeys.all, queryFn: catalogAdminApi.list });
}

export function useCatalogAdminMutations() {
  const queryClient = useQueryClient();
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: catalogAdminKeys.all });
    // Requesters' view of the catalog (environments, limits) changes too.
    void queryClient.invalidateQueries({ queryKey: catalogKeys.all });
  };
  return {
    updateType: useMutation({
      mutationFn: ({ type, input }: { type: AdminResourceTypeResponse; input: Parameters<typeof catalogAdminApi.updateType>[1] }) =>
        catalogAdminApi.updateType(type, input),
      onSuccess: refresh,
    }),
    updatePolicy: useMutation({
      mutationFn: ({ code, policy, input }: { code: ResourceTypeCode; policy: PolicyResponse; input: PolicyInput }) =>
        catalogAdminApi.updatePolicy(code, policy, input),
      onSuccess: refresh,
    }),
  };
}

