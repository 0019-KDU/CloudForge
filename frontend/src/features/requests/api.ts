import { api } from "@/lib/api/client";
import { ApiError } from "@/lib/api/problem";
import type {
  AuditEventResponse,
  DecisionResponse,
  Environment,
  JsonObject,
  PageResponse,
  ProvisioningResponse,
  RequestListScope,
  RequestResponse,
  RequestStatus,
  ResourceTypeCode,
  RetryResponse,
  UUID,
} from "@/types/api";

export interface RequestListParams {
  scope: RequestListScope;
  status?: RequestStatus[] | undefined;
  resourceType?: ResourceTypeCode | undefined;
  environment?: Environment | undefined;
  q?: string | undefined;
  page?: number;
  size?: number;
  sort?: string | undefined;
}

export interface RequestInput {
  environment: Environment;
  spec: JsonObject;
  justification: string;
}

/** 404 means "not there yet" for decision and provisioning lookups. */
async function orNullOn404<T>(promise: Promise<T>, code: string): Promise<T | null> {
  try {
    return await promise;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404 && error.code === code) return null;
    throw error;
  }
}

export const requestsApi = {
  list: (params: RequestListParams) =>
    api.get<PageResponse<RequestResponse>>("/requests", { params }).then((r) => r.data),
  get: (id: UUID) => api.get<RequestResponse>(`/requests/${id}`).then((r) => r.data),
  create: (input: RequestInput & { resourceType: ResourceTypeCode }) =>
    api.post<RequestResponse>("/requests", input).then((r) => r.data),
  updateDraft: (id: UUID, input: RequestInput) => api.put<RequestResponse>(`/requests/${id}`, input).then((r) => r.data),
  deleteDraft: (id: UUID) => api.delete(`/requests/${id}`).then(() => undefined),
  submit: (id: UUID) => api.post<RequestResponse>(`/requests/${id}/submit`).then((r) => r.data),
  cancel: (id: UUID) => api.post<RequestResponse>(`/requests/${id}/cancel`).then((r) => r.data),

  approve: (id: UUID, comment: string | undefined) =>
    api.post<DecisionResponse>(`/requests/${id}/approve`, comment ? { comment } : {}).then((r) => r.data),
  reject: (id: UUID, comment: string) =>
    api.post<DecisionResponse>(`/requests/${id}/reject`, { comment }).then((r) => r.data),
  decision: (id: UUID) =>
    orNullOn404(
      api.get<DecisionResponse>(`/requests/${id}/approval`).then((r) => r.data),
      "DECISION_NOT_FOUND",
    ),

  audit: (id: UUID, page: number, size = 100) =>
    api
      .get<PageResponse<AuditEventResponse>>(`/requests/${id}/audit`, { params: { page, size } })
      .then((r) => r.data),

  provisioning: (id: UUID) =>
    orNullOn404(
      api.get<ProvisioningResponse>(`/requests/${id}/provisioning`).then((r) => r.data),
      "PROVISIONING_NOT_FOUND",
    ),
  retryProvisioning: (id: UUID) =>
    api.post<RetryResponse>(`/requests/${id}/provisioning/retry`).then((r) => r.data),
};
