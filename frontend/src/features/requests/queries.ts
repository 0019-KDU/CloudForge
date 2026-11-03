import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import type { RequestStatus, UUID } from "@/types/api";

import { requestsApi, type RequestInput, type RequestListParams } from "./api";

export const requestKeys = {
  all: ["requests"] as const,
  lists: () => [...requestKeys.all, "list"] as const,
  list: (params: RequestListParams) => [...requestKeys.lists(), params] as const,
  counts: () => [...requestKeys.all, "count"] as const,
  count: (params: RequestListParams) => [...requestKeys.counts(), params] as const,
  detail: (id: UUID) => [...requestKeys.all, "detail", id] as const,
  decision: (id: UUID) => [...requestKeys.detail(id), "decision"] as const,
  audit: (id: UUID) => [...requestKeys.detail(id), "audit"] as const,
  provisioning: (id: UUID) => [...requestKeys.detail(id), "provisioning"] as const,
};

/** Statuses during which the backend worker changes the request without user action. */
export const IN_FLIGHT: readonly RequestStatus[] = ["APPROVED", "PROVISIONING"];
const POLL_MS = 5_000;

export function useRequestList(params: RequestListParams) {
  return useQuery({
    queryKey: requestKeys.list(params),
    queryFn: () => requestsApi.list(params),
    placeholderData: keepPreviousData,
  });
}

/** Total matching requests, from a one-row page: real counts without a dedicated stats endpoint. */
export function useRequestCount(params: Omit<RequestListParams, "page" | "size" | "sort">) {
  const query = { ...params, page: 0, size: 1 };
  return useQuery({
    queryKey: requestKeys.count(query),
    queryFn: () => requestsApi.list(query),
    select: (page) => page.totalElements,
  });
}

export function useRequest(id: UUID) {
  return useQuery({
    queryKey: requestKeys.detail(id),
    queryFn: () => requestsApi.get(id),
    refetchInterval: (query) => (query.state.data && IN_FLIGHT.includes(query.state.data.status) ? POLL_MS : false),
  });
}

export function useDecision(id: UUID, enabled: boolean) {
  return useQuery({ queryKey: requestKeys.decision(id), queryFn: () => requestsApi.decision(id), enabled });
}

export function useRequestAudit(id: UUID, polling: boolean) {
  return useQuery({
    queryKey: requestKeys.audit(id),
    queryFn: () => requestsApi.audit(id, 0),
    refetchInterval: polling ? POLL_MS : false,
  });
}

export function useProvisioning(id: UUID, enabled: boolean, polling: boolean) {
  return useQuery({
    queryKey: requestKeys.provisioning(id),
    queryFn: () => requestsApi.provisioning(id),
    enabled,
    refetchInterval: polling ? POLL_MS : false,
  });
}

/** After any change to a request: its detail, every list and the dashboard counters are stale. */
export function invalidateRequest(queryClient: QueryClient, id?: UUID) {
  void queryClient.invalidateQueries({ queryKey: requestKeys.lists() });
  void queryClient.invalidateQueries({ queryKey: requestKeys.counts() });
  void queryClient.invalidateQueries({ queryKey: ["provisioning-jobs"] });
  if (id) void queryClient.invalidateQueries({ queryKey: requestKeys.detail(id) });
}

export function useRequestMutations(id: UUID) {
  const queryClient = useQueryClient();
  const onSuccess = () => invalidateRequest(queryClient, id);
  return {
    submit: useMutation({ mutationFn: () => requestsApi.submit(id), onSuccess }),
    cancel: useMutation({ mutationFn: () => requestsApi.cancel(id), onSuccess }),
    remove: useMutation({ mutationFn: () => requestsApi.deleteDraft(id), onSuccess: () => invalidateRequest(queryClient) }),
    update: useMutation({ mutationFn: (input: RequestInput) => requestsApi.updateDraft(id, input), onSuccess }),
    approve: useMutation({ mutationFn: (comment: string | undefined) => requestsApi.approve(id, comment), onSuccess }),
    reject: useMutation({ mutationFn: (comment: string) => requestsApi.reject(id, comment), onSuccess }),
    retry: useMutation({ mutationFn: () => requestsApi.retryProvisioning(id), onSuccess }),
  };
}
