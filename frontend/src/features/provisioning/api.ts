import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api/client";
import type { JobStatus, PageResponse, ProvisioningJobSummary, ResourceTypeCode } from "@/types/api";

export interface JobListParams {
  status?: JobStatus[] | undefined;
  resourceType?: ResourceTypeCode | undefined;
  page?: number;
  size?: number;
}

export const provisioningApi = {
  jobs: (params: JobListParams) =>
    api.get<PageResponse<ProvisioningJobSummary>>("/provisioning-jobs", { params }).then((r) => r.data),
};

export const jobKeys = {
  all: ["provisioning-jobs"] as const,
  list: (params: JobListParams) => [...jobKeys.all, params] as const,
};

/** The queue changes without user action, so the operations view refreshes itself. */
export function useJobs(params: JobListParams) {
  return useQuery({
    queryKey: jobKeys.list(params),
    queryFn: () => provisioningApi.jobs(params),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  });
}

export function useJobCount(status: JobStatus[]) {
  const params = { status, page: 0, size: 1 };
  return useQuery({
    queryKey: jobKeys.list(params),
    queryFn: () => provisioningApi.jobs(params),
    select: (page) => page.totalElements,
  });
}
