import { http, HttpResponse } from "msw";

import type {
  AuditEventResponse,
  DecisionResponse,
  DirectoryEntry,
  Environment,
  JsonObject,
  PageResponse,
  ProvisioningResponse,
  RequestResponse,
  RequestStatus,
  ResourceTypeCode,
  ResourceTypeResponse,
  UserResponse,
} from "@/types/api";

import { ids, problem, tokens, users } from "./fixtures";
import { apiUrl, server } from "./server";

/**
 * A small stateful stand-in for the CloudForge API, for workflow tests. It follows the real
 * contract (paths, DTO shapes, scopes, status transitions, 404 masking, error codes) closely enough
 * to drive the UI end to end; the backend's own tests cover the real rules.
 */
export interface FakeDb {
  me: UserResponse;
  people: UserResponse[];
  requests: RequestResponse[];
  decisions: Map<string, DecisionResponse>;
  provisioning: Map<string, ProvisioningResponse>;
  audit: AuditEventResponse[];
  catalog: ResourceTypeResponse[];
  calls: { method: string; path: string; body: unknown; search: string }[];
}

export const catalogFixture: ResourceTypeResponse[] = [
  {
    code: "K8S_NAMESPACE",
    provider: "KUBERNETES",
    displayName: "Kubernetes namespace",
    description: "A namespace on the shared cluster with a CPU and memory quota.",
    allowedEnvironments: ["DEVELOPMENT", "STAGING"],
    environments: [
      { environment: "DEVELOPMENT", maxActivePerUser: 3, constraints: { maxCpuCores: 8, maxMemoryGi: 32 } },
      { environment: "STAGING", maxActivePerUser: null, constraints: { maxCpuCores: 4, maxMemoryGi: 16 } },
    ],
  },
  {
    code: "AWS_ACCOUNT",
    provider: "AWS",
    displayName: "AWS account",
    description: "A member account in the AWS Organization.",
    allowedEnvironments: ["DEVELOPMENT"],
    environments: [
      {
        environment: "DEVELOPMENT",
        maxActivePerUser: null,
        constraints: { allowedOrganizationalUnitIds: ["ou-ab12-cd34ef56"], allowedEmailDomains: ["acme.com"] },
      },
    ],
  },
  {
    code: "GCP_PROJECT",
    provider: "GCP",
    displayName: "GCP project",
    description: "A Google Cloud project under an approved folder.",
    allowedEnvironments: [],
    environments: [],
  },
];

let sequence = 100;
const now = () => new Date("2026-10-30T12:00:00Z").toISOString();

export function makeRequest(overrides: Partial<RequestResponse> = {}): RequestResponse {
  sequence++;
  return {
    id: `0192f0a0-0000-7000-8000-${String(sequence).padStart(12, "0")}`,
    requestNumber: `CF-2026-${String(sequence).padStart(6, "0")}`,
    requesterId: ids.engineer,
    assignedApproverId: ids.manager,
    resourceType: "K8S_NAMESPACE",
    environment: "DEVELOPMENT",
    spec: { namespace: `team-a-${sequence}`, cpuCores: 2, memoryGi: 4 },
    justification: "Integration environment for the payments team.",
    status: "PENDING_APPROVAL",
    submittedAt: now(),
    decidedAt: null,
    createdAt: now(),
    updatedAt: now(),
    ...overrides,
  };
}

function page<T>(items: T[], url: URL): PageResponse<T> {
  const number = Number(url.searchParams.get("page") ?? 0);
  const size = Number(url.searchParams.get("size") ?? 20);
  return {
    content: items.slice(number * size, number * size + size),
    page: number,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

const notFound = () => HttpResponse.json(problem(404, "REQUEST_NOT_FOUND", "Request was not found."), { status: 404 });
const forbidden = () =>
  HttpResponse.json(problem(403, "ACCESS_DENIED", "You do not have permission to perform this operation."), { status: 403 });
const invalidTransition = () =>
  HttpResponse.json(problem(422, "INVALID_STATE_TRANSITION", "The request can no longer be changed this way."), { status: 422 });

function canView(db: FakeDb, request: RequestResponse) {
  const { me } = db;
  return (
    request.requesterId === me.id ||
    request.assignedApproverId === me.id ||
    me.roles.includes("PLATFORM_ADMIN") ||
    me.roles.includes("AUDITOR")
  );
}

function record(db: FakeDb, action: string, entityId: string, details: JsonObject = {}, system = false) {
  db.audit.push({
    id: `audit-${db.audit.length + 1}`,
    occurredAt: now(),
    actorType: system ? "SYSTEM" : "USER",
    actorId: system ? null : db.me.id,
    action,
    entityType: "RESOURCE_REQUEST",
    entityId,
    correlationId: "test-correlation-id",
    details,
  });
}

/** Installs the fake API, signed in as `me`. Returns the mutable state for assertions. */
export function useFakeBackend(me: UserResponse, seed: { requests?: RequestResponse[] } = {}): FakeDb {
  const db: FakeDb = {
    me,
    people: Object.values(users),
    requests: [...(seed.requests ?? [])],
    decisions: new Map(),
    provisioning: new Map(),
    audit: [],
    catalog: structuredClone(catalogFixture),
    calls: [],
  };

  const find = (id: string | readonly string[] | undefined) => db.requests.find((request) => request.id === id);
  const log = async (request: Request) => {
    const url = new URL(request.url);
    const text = request.method === "GET" ? "" : await request.clone().text();
    db.calls.push({ method: request.method, path: url.pathname, body: text ? (JSON.parse(text) as unknown) : null, search: url.search });
  };

  server.use(
    http.post(apiUrl("/auth/refresh"), () => HttpResponse.json(tokens())),
    http.get(apiUrl("/users/me"), () => HttpResponse.json(db.me)),
    http.get(apiUrl("/users/directory"), ({ request }) => {
      const wanted = new URL(request.url).searchParams.get("ids")?.split(",") ?? [];
      const entries: DirectoryEntry[] = db.people
        .filter((person) => wanted.includes(person.id))
        .map(({ id, displayName, email, status }) => ({ id, displayName, email, status }));
      return HttpResponse.json(entries);
    }),
    http.get(apiUrl("/notifications/unread-count"), () => HttpResponse.json({ unread: 0 })),
    http.get(apiUrl("/notifications"), ({ request }) => HttpResponse.json(page([], new URL(request.url)))),
    http.get(apiUrl("/resource-types"), () => HttpResponse.json(db.catalog)),

    http.post(apiUrl("/resource-types/:code/spec-validation"), async ({ request }) => {
      await log(request);
      const body = (await request.json()) as { spec: JsonObject };
      if (body.spec.namespace === "taken-by-policy") {
        return HttpResponse.json(
          problem(422, "INVALID_RESOURCE_SPEC", "The resource specification is invalid.", {
            errors: [{ field: "spec.namespace", message: "is not allowed by the policy" }],
          }),
          { status: 422 },
        );
      }
      return HttpResponse.json({ valid: true });
    }),

    http.get(apiUrl("/requests"), async ({ request }) => {
      await log(request);
      const url = new URL(request.url);
      const scope = url.searchParams.get("scope") ?? "MINE";
      if (scope === "ALL" && !me.roles.some((role) => role === "PLATFORM_ADMIN" || role === "AUDITOR")) return forbidden();
      if (scope === "ASSIGNED" && !me.roles.includes("MANAGER")) return forbidden();
      const statuses = url.searchParams.getAll("status") as RequestStatus[];
      const type = url.searchParams.get("resourceType") as ResourceTypeCode | null;
      const environment = url.searchParams.get("environment") as Environment | null;
      const q = url.searchParams.get("q")?.toLowerCase();
      const rows = db.requests.filter(
        (r) =>
          (scope !== "MINE" || r.requesterId === me.id) &&
          (scope !== "ASSIGNED" || r.assignedApproverId === me.id) &&
          (statuses.length === 0 || statuses.includes(r.status)) &&
          (!type || r.resourceType === type) &&
          (!environment || r.environment === environment) &&
          (!q || `${r.requestNumber} ${r.justification}`.toLowerCase().includes(q)),
      );
      return HttpResponse.json(page(rows, url));
    }),

    http.post(apiUrl("/requests"), async ({ request }) => {
      await log(request);
      if (!me.roles.some((role) => role === "ENGINEER" || role === "MANAGER")) return forbidden();
      const body = (await request.json()) as Pick<RequestResponse, "resourceType" | "environment" | "spec" | "justification">;
      const created = makeRequest({ ...body, requesterId: me.id, assignedApproverId: null, status: "DRAFT", submittedAt: null });
      db.requests.push(created);
      record(db, "REQUEST_CREATED", created.id, { requestNumber: created.requestNumber });
      return HttpResponse.json(created, { status: 201 });
    }),

    http.get(apiUrl("/requests/:id"), ({ params }) => {
      const found = find(params.id);
      return found && canView(db, found) ? HttpResponse.json(found) : notFound();
    }),

    http.post(apiUrl("/requests/:id/submit"), async ({ params, request }) => {
      await log(request);
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      if (found.requesterId !== me.id) return forbidden();
      if (!me.managerId) {
        return HttpResponse.json(
          problem(422, "REQUESTER_HAS_NO_MANAGER", "You have no manager to approve this request. Ask an administrator to assign one."),
          { status: 422 },
        );
      }
      if (found.status !== "DRAFT") return invalidTransition();
      Object.assign(found, { status: "PENDING_APPROVAL", assignedApproverId: me.managerId, submittedAt: now() });
      record(db, "REQUEST_SUBMITTED", found.id, { approverId: me.managerId });
      return HttpResponse.json(found);
    }),

    http.post(apiUrl("/requests/:id/cancel"), async ({ params, request }) => {
      await log(request);
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      if (found.status !== "PENDING_APPROVAL") return invalidTransition();
      found.status = "CANCELLED";
      return HttpResponse.json(found);
    }),

    http.post(apiUrl("/requests/:id/approve"), async ({ params, request }) => {
      await log(request);
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      if (found.assignedApproverId !== me.id || !me.roles.includes("MANAGER")) return forbidden();
      if (found.status !== "PENDING_APPROVAL") return invalidTransition();
      const body = (await request.json().catch(() => ({}))) as { comment?: string };
      Object.assign(found, { status: "APPROVED", decidedAt: now() });
      db.decisions.set(found.id, {
        id: `decision-${found.id}`,
        requestId: found.id,
        approverId: me.id,
        decision: "APPROVED",
        comment: body.comment ?? null,
        decidedAt: now(),
      });
      db.provisioning.set(found.id, {
        status: "QUEUED",
        attemptCount: 0,
        nextRunAt: now(),
        lastErrorCode: null,
        lastErrorMessage: null,
        awaitingProvider: false,
        result: null,
        attempts: [],
      });
      record(db, "REQUEST_APPROVED", found.id, body.comment ? { comment: body.comment } : {});
      return HttpResponse.json(db.decisions.get(found.id));
    }),

    http.post(apiUrl("/requests/:id/reject"), async ({ params, request }) => {
      await log(request);
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      if (found.assignedApproverId !== me.id) return forbidden();
      const body = (await request.json()) as { comment?: string };
      if (!body.comment?.trim()) {
        return HttpResponse.json(
          problem(400, "VALIDATION_FAILED", "The request contains invalid fields.", { errors: [{ field: "comment", message: "must not be blank" }] }),
          { status: 400 },
        );
      }
      Object.assign(found, { status: "REJECTED", decidedAt: now() });
      db.decisions.set(found.id, {
        id: `decision-${found.id}`,
        requestId: found.id,
        approverId: me.id,
        decision: "REJECTED",
        comment: body.comment,
        decidedAt: now(),
      });
      record(db, "REQUEST_REJECTED", found.id, { comment: body.comment });
      return HttpResponse.json(db.decisions.get(found.id));
    }),

    http.get(apiUrl("/requests/:id/approval"), ({ params }) => {
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      const decision = db.decisions.get(found.id);
      return decision
        ? HttpResponse.json(decision)
        : HttpResponse.json(problem(404, "DECISION_NOT_FOUND", "This request has not been decided yet."), { status: 404 });
    }),

    http.get(apiUrl("/requests/:id/provisioning"), ({ params }) => {
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      const job = db.provisioning.get(found.id);
      return job
        ? HttpResponse.json(job)
        : HttpResponse.json(problem(404, "PROVISIONING_NOT_FOUND", "This request has no provisioning job."), { status: 404 });
    }),

    http.post(apiUrl("/requests/:id/provisioning/retry"), async ({ params, request }) => {
      await log(request);
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      if (!me.roles.includes("PLATFORM_ADMIN")) return forbidden();
      const job = db.provisioning.get(found.id);
      if (!job || job.status !== "FAILED") return invalidTransition();
      Object.assign(job, { status: "QUEUED", lastErrorCode: null, lastErrorMessage: null });
      found.status = "PROVISIONING";
      return HttpResponse.json({ status: "QUEUED", nextRunAt: now() });
    }),

    http.get(apiUrl("/requests/:id/audit"), ({ params, request }) => {
      const found = find(params.id);
      if (!found || !canView(db, found)) return notFound();
      return HttpResponse.json(page(db.audit.filter((event) => event.entityId === found.id), new URL(request.url)));
    }),
  );
  return db;
}
