import type { CloudProvider, Environment, RequestStatus, ResourceTypeCode, Role } from "@/types/api";

/** Human labels for backend enums. Unknown values fall back to a humanized form. */

const ROLE_LABELS: Record<Role, string> = {
  ENGINEER: "Engineer",
  MANAGER: "Manager",
  PLATFORM_ADMIN: "Platform admin",
  AUDITOR: "Auditor",
};

const STATUS_LABELS: Record<RequestStatus, string> = {
  DRAFT: "Draft",
  PENDING_APPROVAL: "Pending approval",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  PROVISIONING: "Provisioning",
  PROVISIONED: "Provisioned",
  PROVISIONING_FAILED: "Provisioning failed",
};

const ENVIRONMENT_LABELS: Record<Environment, string> = {
  DEVELOPMENT: "Development",
  STAGING: "Staging",
  PRODUCTION: "Production",
};

const RESOURCE_TYPE_LABELS: Record<ResourceTypeCode, string> = {
  AWS_ACCOUNT: "AWS account",
  GCP_PROJECT: "GCP project",
  K8S_NAMESPACE: "Kubernetes namespace",
};

export function humanize(value: string): string {
  const text = value.toLowerCase().replace(/_/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "organizationalUnitId" → "Organizational unit id". */
export function humanizeKey(key: string) {
  return humanize(key.replace(/([a-z0-9])([A-Z])/g, "$1_$2"));
}

const PROVIDER_OF: Record<ResourceTypeCode, CloudProvider> = {
  AWS_ACCOUNT: "AWS",
  GCP_PROJECT: "GCP",
  K8S_NAMESPACE: "KUBERNETES",
};

const PROVIDER_LABELS: Record<CloudProvider, string> = { AWS: "AWS", GCP: "Google Cloud", KUBERNETES: "Kubernetes" };

export const providerOf = (code: ResourceTypeCode) => PROVIDER_OF[code];
export const providerLabel = (provider: CloudProvider) => PROVIDER_LABELS[provider];

export const roleLabel = (role: Role) => ROLE_LABELS[role];
export const statusLabel = (status: RequestStatus) => STATUS_LABELS[status];
export const environmentLabel = (environment: Environment) => ENVIRONMENT_LABELS[environment];
export const resourceTypeLabel = (code: ResourceTypeCode) => RESOURCE_TYPE_LABELS[code];
