/**
 * Backend DTOs and enums, mirrored from the Spring Boot controllers (see
 * docs/frontend/api-integration.md). Keep these in sync with the backend; never add fields the
 * backend does not send.
 */

export type UUID = string;
/** ISO-8601 instant in UTC, e.g. "2026-10-30T12:00:00Z". */
export type Instant = string;
export type JsonObject = Record<string, unknown>;

// ---- Enums --------------------------------------------------------------------------------

export const ROLES = ["ENGINEER", "MANAGER", "PLATFORM_ADMIN", "AUDITOR"] as const;
export type Role = (typeof ROLES)[number];

export type UserStatus = "ACTIVE" | "DISABLED";

export const RESOURCE_TYPE_CODES = ["AWS_ACCOUNT", "GCP_PROJECT", "K8S_NAMESPACE"] as const;
export type ResourceTypeCode = (typeof RESOURCE_TYPE_CODES)[number];

export type CloudProvider = "AWS" | "GCP" | "KUBERNETES";

export const ENVIRONMENTS = ["DEVELOPMENT", "STAGING", "PRODUCTION"] as const;
export type Environment = (typeof ENVIRONMENTS)[number];

export const REQUEST_STATUSES = [
  "DRAFT",
  "PENDING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "CANCELLED",
  "PROVISIONING",
  "PROVISIONED",
  "PROVISIONING_FAILED",
] as const;
export type RequestStatus = (typeof REQUEST_STATUSES)[number];

export type RequestListScope = "MINE" | "ASSIGNED" | "ALL";

export const JOB_STATUSES = ["QUEUED", "RUNNING", "AWAITING_PROVIDER", "SUCCEEDED", "FAILED"] as const;
export type JobStatus = (typeof JOB_STATUSES)[number];

export type AttemptKind = "START" | "POLL";
export type AttemptOutcome = "SUCCEEDED" | "IN_PROGRESS" | "RETRYABLE_FAILURE" | "PERMANENT_FAILURE";

export type Decision = "APPROVED" | "REJECTED";

export type NotificationType =
  | "APPROVAL_REQUESTED"
  | "REQUEST_APPROVED"
  | "REQUEST_REJECTED"
  | "PROVISIONING_SUCCEEDED"
  | "PROVISIONING_FAILED";

export type InvitationStatus = "PENDING" | "ACCEPTED" | "REVOKED";

export const AUDIT_ENTITY_TYPES = ["RESOURCE_REQUEST", "USER", "INVITATION", "DEPARTMENT", "TEAM", "RESOURCE_TYPE"] as const;
export type AuditEntityType = (typeof AUDIT_ENTITY_TYPES)[number];

export const AUDIT_ACTIONS = [
  "REQUEST_CREATED",
  "REQUEST_UPDATED",
  "REQUEST_DELETED",
  "REQUEST_SUBMITTED",
  "REQUEST_CANCELLED",
  "REQUEST_APPROVED",
  "REQUEST_REJECTED",
  "PROVISIONING_QUEUED",
  "PROVISIONING_STARTED",
  "PROVISIONING_ATTEMPT_FAILED",
  "PROVISIONING_SUCCEEDED",
  "PROVISIONING_FAILED",
  "PROVISIONING_RETRY_REQUESTED",
  "USER_REGISTERED",
  "USER_MANAGER_CHANGED",
  "USER_TEAM_CHANGED",
  "USER_ROLES_CHANGED",
  "USER_DISABLED",
  "USER_ENABLED",
  "PROFILE_UPDATED",
  "PASSWORD_CHANGED",
  "PASSWORD_RESET_ISSUED",
  "PASSWORD_RESET_COMPLETED",
  "INVITATION_CREATED",
  "INVITATION_REVOKED",
  "LOGIN_SUCCEEDED",
  "LOGIN_FAILED",
  "ACCOUNT_LOCKED",
  "REFRESH_TOKEN_REUSE_DETECTED",
  "DEPARTMENT_CREATED",
  "DEPARTMENT_RENAMED",
  "TEAM_CREATED",
  "TEAM_RENAMED",
  "RESOURCE_TYPE_UPDATED",
  "RESOURCE_POLICY_UPDATED",
] as const;
/** Known actions; the backend may add more, so displayed values are typed as plain strings. */
export type AuditAction = (typeof AUDIT_ACTIONS)[number];

// ---- Common -------------------------------------------------------------------------------

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldViolation {
  field: string;
  message: string;
}

/** RFC 9457 problem body with CloudForge's extra members. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  correlationId: string | null;
  timestamp: Instant;
  errors?: FieldViolation[];
}

// ---- Identity -----------------------------------------------------------------------------

export interface TokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  /** Seconds. */
  expiresIn: number;
}

export interface UserResponse {
  id: UUID;
  email: string;
  displayName: string;
  status: UserStatus;
  roles: Role[];
  managerId: UUID | null;
  teamId: UUID | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface RegisteredUserResponse {
  id: UUID;
  email: string;
  displayName: string;
  roles: Role[];
}

export interface DirectoryEntry {
  id: UUID;
  displayName: string;
  email: string;
  status: UserStatus;
}

export interface InvitationResponse {
  id: UUID;
  email: string;
  status: InvitationStatus;
  expired: boolean;
  roles: Role[];
  managerId: UUID | null;
  teamId: UUID | null;
  invitedBy: UUID;
  expiresAt: Instant;
  createdAt: Instant;
}

export interface IssuedInvitationResponse {
  invitation: InvitationResponse;
  token: string;
}

export interface PasswordResetResponse {
  token: string;
  expiresAt: Instant;
}

// ---- Organization -------------------------------------------------------------------------

export interface DepartmentResponse {
  id: UUID;
  name: string;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface TeamResponse {
  id: UUID;
  departmentId: UUID;
  name: string;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

// ---- Catalog ------------------------------------------------------------------------------

export interface EnvironmentPolicy {
  environment: Environment;
  maxActivePerUser: number | null;
  constraints: JsonObject | null;
}

export interface ResourceTypeResponse {
  code: ResourceTypeCode;
  provider: CloudProvider;
  displayName: string;
  description: string;
  allowedEnvironments: Environment[];
  environments: EnvironmentPolicy[];
}

export interface PolicyResponse {
  environment: Environment;
  allowed: boolean;
  maxActivePerUser: number | null;
  constraints: JsonObject | null;
  /** null when the environment has no stored policy yet. */
  version: number | null;
}

export interface AdminResourceTypeResponse {
  code: ResourceTypeCode;
  provider: CloudProvider;
  displayName: string;
  description: string;
  enabled: boolean;
  version: number;
  policies: PolicyResponse[];
}

// ---- Requests and approval ----------------------------------------------------------------

export interface RequestResponse {
  id: UUID;
  requestNumber: string;
  requesterId: UUID;
  assignedApproverId: UUID | null;
  resourceType: ResourceTypeCode;
  environment: Environment;
  spec: JsonObject;
  justification: string;
  status: RequestStatus;
  submittedAt: Instant | null;
  decidedAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
}

export interface DecisionResponse {
  id: UUID;
  requestId: UUID;
  approverId: UUID;
  decision: Decision;
  comment: string | null;
  decidedAt: Instant;
}

// ---- Provisioning -------------------------------------------------------------------------

export interface ProvisioningAttemptResponse {
  attemptNumber: number;
  kind: AttemptKind;
  outcome: AttemptOutcome;
  startedAt: Instant;
  finishedAt: Instant;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface ProvisioningResultResponse {
  externalResourceId: string;
  details: JsonObject;
  createdAt: Instant;
}

export interface ProvisioningResponse {
  status: JobStatus;
  attemptCount: number;
  nextRunAt: Instant | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
  awaitingProvider: boolean;
  result: ProvisioningResultResponse | null;
  attempts: ProvisioningAttemptResponse[];
}

export interface RetryResponse {
  status: JobStatus;
  nextRunAt: Instant | null;
}

export interface ProvisioningJobSummary {
  jobId: UUID;
  requestId: UUID;
  requestNumber: string;
  resourceType: ResourceTypeCode;
  status: JobStatus;
  attemptCount: number;
  nextRunAt: Instant | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
  createdAt: Instant;
  updatedAt: Instant;
}

// ---- Notifications ------------------------------------------------------------------------

export interface NotificationResponse {
  id: UUID;
  type: NotificationType;
  title: string;
  body: string;
  requestId: UUID | null;
  readAt: Instant | null;
  createdAt: Instant;
}

// ---- Audit --------------------------------------------------------------------------------

export interface AuditEventResponse {
  id: UUID;
  occurredAt: Instant;
  actorType: "USER" | "SYSTEM";
  actorId: UUID | null;
  action: string;
  entityType: AuditEntityType;
  entityId: UUID;
  correlationId: string | null;
  details: JsonObject;
}
