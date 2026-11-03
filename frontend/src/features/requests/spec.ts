import { z } from "zod";

import type { EnvironmentPolicy, JsonObject, ResourceTypeCode } from "@/types/api";

/**
 * Per-type request specifications, mirroring the backend's *SpecValidator classes
 * (resourcecatalog/spec). The backend re-validates everything; these rules give early feedback and
 * turn policy constraints into choices (approved OUs and folders, CPU and memory caps).
 *
 * Form values are strings (what inputs produce); `toSpec` converts them to the JSON the API expects.
 */

export type SpecValues = Record<string, string>;

export interface SpecField {
  name: string;
  label: string;
  hint?: string;
  placeholder?: string;
  optional?: boolean;
  /** Choices from the policy; the field becomes a select. */
  options?: string[];
  kind: "text" | "number";
  /** Policy maximum for numbers. */
  max?: number;
  unit?: string;
  monospace?: boolean;
}

/** What a policy allows, parsed defensively: constraints are untyped JSON. */
export interface ParsedConstraints {
  allowedOrganizationalUnitIds: string[];
  allowedEmailDomains: string[];
  allowedFolderIds: string[];
  maxCpuCores: number | undefined;
  maxMemoryGi: number | undefined;
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function positiveNumber(value: unknown): number | undefined {
  return typeof value === "number" && value > 0 ? value : undefined;
}

export function parseConstraints(constraints: JsonObject | null | undefined): ParsedConstraints {
  const c = constraints ?? {};
  return {
    allowedOrganizationalUnitIds: stringList(c.allowedOrganizationalUnitIds),
    allowedEmailDomains: stringList(c.allowedEmailDomains),
    allowedFolderIds: stringList(c.allowedFolderIds),
    maxCpuCores: positiveNumber(c.maxCpuCores),
    maxMemoryGi: positiveNumber(c.maxMemoryGi),
  };
}

// Patterns copied from the backend validators.
const AWS_ACCOUNT_NAME = /^[\x20-\x7E]{1,50}$/;
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const OU_ID = /^ou-[0-9a-z]{4,32}-[a-z0-9]{8,32}$/;
const GCP_PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/;
const GCP_DISPLAY_NAME = /^[A-Za-z0-9' !-]{4,30}$/;
const FOLDER_ID = /^[0-9]{1,20}$/;
const DNS_LABEL = /^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$/;

/** At most `scale` decimals, as the backend's requiredPositiveNumber(…, 3). */
function decimalPlaces(value: string) {
  const [, fraction = ""] = value.split(".");
  return fraction.length;
}

function positiveDecimal(label: string, max: number | undefined) {
  return z
    .string()
    .trim()
    .min(1, `Enter the ${label}.`)
    .refine((value) => /^\d+(\.\d+)?$/.test(value) && Number(value) > 0, { message: "Enter a number greater than zero." })
    .refine((value) => decimalPlaces(value) <= 3, { message: "Use at most 3 decimal places." })
    .refine((value) => max === undefined || Number(value) <= max, {
      message: `The policy allows at most ${String(max)}.`,
    });
}

interface TypeDefinition {
  fields: (c: ParsedConstraints) => SpecField[];
  schema: (c: ParsedConstraints) => z.ZodType<SpecValues>;
  toSpec: (values: SpecValues) => JsonObject;
  /** The field that names the resource, for tables and summaries. */
  nameField: string;
}

const definitions: Record<ResourceTypeCode, TypeDefinition> = {
  AWS_ACCOUNT: {
    nameField: "accountName",
    fields: (c) => [
      { name: "accountName", label: "Account name", kind: "text", placeholder: "payments-dev", hint: "1–50 characters." },
      {
        name: "email",
        label: "Root email address",
        kind: "text",
        placeholder: c.allowedEmailDomains[0] ? `aws-payments-dev@${c.allowedEmailDomains[0]}` : "aws-team@example.com",
        hint:
          c.allowedEmailDomains.length > 0
            ? `Must be unique across AWS and use ${c.allowedEmailDomains.map((d) => `@${d}`).join(" or ")}.`
            : "Must be unique across all of AWS.",
      },
      {
        name: "organizationalUnitId",
        label: "Organizational unit",
        kind: "text",
        options: c.allowedOrganizationalUnitIds,
        monospace: true,
        hint: "Where the account is placed in the AWS Organization.",
      },
    ],
    schema: (c) =>
      z.object({
        accountName: z.string().trim().regex(AWS_ACCOUNT_NAME, "Use 1–50 printable characters."),
        email: z
          .string()
          .trim()
          .regex(EMAIL, "Enter a valid email address.")
          .refine((v) => v.length >= 6 && v.length <= 64, { message: "Use 6–64 characters." })
          .refine(
            (v) =>
              c.allowedEmailDomains.length === 0 ||
              c.allowedEmailDomains.includes(v.slice(v.indexOf("@") + 1).toLowerCase()),
            { message: "Use one of the approved email domains." },
          ),
        organizationalUnitId: z
          .string()
          .min(1, "Choose an organizational unit.")
          .regex(OU_ID, "Not a valid organizational unit ID.")
          .refine((v) => c.allowedOrganizationalUnitIds.includes(v), { message: "Choose an approved organizational unit." }),
      }),
    toSpec: (v) => ({
      accountName: v.accountName?.trim(),
      email: v.email?.trim(),
      organizationalUnitId: v.organizationalUnitId,
    }),
  },

  GCP_PROJECT: {
    nameField: "projectId",
    fields: (c) => [
      {
        name: "projectId",
        label: "Project ID",
        kind: "text",
        placeholder: "acme-payments-dev",
        monospace: true,
        hint: "6–30 lowercase letters, digits and hyphens; starts with a letter. Globally unique and permanent.",
      },
      {
        name: "displayName",
        label: "Display name",
        kind: "text",
        optional: true,
        placeholder: "Payments dev",
        hint: "4–30 letters, digits, spaces, hyphens, apostrophes or exclamation points.",
      },
      { name: "folderId", label: "Folder", kind: "text", options: c.allowedFolderIds, monospace: true },
    ],
    schema: (c) =>
      z.object({
        projectId: z
          .string()
          .trim()
          .regex(GCP_PROJECT_ID, "Use 6–30 lowercase letters, digits and hyphens, starting with a letter and not ending with a hyphen.")
          .refine((v) => !v.includes("google"), { message: "Must not contain the reserved word “google”." }),
        displayName: z
          .string()
          .trim()
          .refine((v) => v === "" || GCP_DISPLAY_NAME.test(v), {
            message: "Use 4–30 letters, digits, spaces, hyphens, apostrophes or exclamation points.",
          }),
        folderId: z
          .string()
          .min(1, "Choose a folder.")
          .regex(FOLDER_ID, "Not a valid folder ID.")
          .refine((v) => c.allowedFolderIds.includes(v), { message: "Choose an approved folder." }),
      }),
    toSpec: (v) => {
      const displayName = v.displayName?.trim();
      return { projectId: v.projectId?.trim(), ...(displayName ? { displayName } : {}), folderId: v.folderId };
    },
  },

  K8S_NAMESPACE: {
    nameField: "namespace",
    fields: (c) => [
      {
        name: "namespace",
        label: "Namespace",
        kind: "text",
        placeholder: "team-a-dev",
        monospace: true,
        hint: "Lowercase letters, digits and hyphens, up to 63 characters.",
      },
      { name: "cpuCores", label: "CPU quota", kind: "number", unit: "cores", ...(c.maxCpuCores ? { max: c.maxCpuCores } : {}) },
      { name: "memoryGi", label: "Memory quota", kind: "number", unit: "GiB", ...(c.maxMemoryGi ? { max: c.maxMemoryGi } : {}) },
    ],
    schema: (c) =>
      z.object({
        namespace: z
          .string()
          .trim()
          .regex(DNS_LABEL, "Use lowercase letters, digits and hyphens, starting and ending with a letter or digit (max 63).")
          .refine((v) => !v.startsWith("kube-") && v !== "default", { message: "This name is reserved by Kubernetes." }),
        cpuCores: positiveDecimal("CPU quota", c.maxCpuCores),
        memoryGi: positiveDecimal("memory quota", c.maxMemoryGi),
      }),
    toSpec: (v) => ({ namespace: v.namespace?.trim(), cpuCores: Number(v.cpuCores), memoryGi: Number(v.memoryGi) }),
  },
};

export function specFields(type: ResourceTypeCode, policy: EnvironmentPolicy | undefined) {
  return definitions[type].fields(parseConstraints(policy?.constraints));
}

export function specSchema(type: ResourceTypeCode, policy: EnvironmentPolicy | undefined) {
  return definitions[type].schema(parseConstraints(policy?.constraints));
}

export function toSpec(type: ResourceTypeCode, values: SpecValues): JsonObject {
  return definitions[type].toSpec(values);
}

/** Back from API JSON to form strings, for editing drafts. */
export function fromSpec(spec: JsonObject): SpecValues {
  const values: SpecValues = {};
  for (const [key, value] of Object.entries(spec)) {
    if (typeof value === "string" || typeof value === "number") values[key] = String(value);
  }
  return values;
}

export function emptySpec(type: ResourceTypeCode): SpecValues {
  return Object.fromEntries(definitions[type].fields(parseConstraints(null)).map((field) => [field.name, ""]));
}

/** The human name of a requested resource, e.g. the namespace or project ID. */
export function resourceName(type: ResourceTypeCode, spec: JsonObject): string | null {
  const value = spec[definitions[type].nameField];
  return typeof value === "string" ? value : null;
}

export const SPEC_LABELS: Record<string, string> = {
  accountName: "Account name",
  email: "Root email address",
  organizationalUnitId: "Organizational unit",
  projectId: "Project ID",
  displayName: "Display name",
  folderId: "Folder",
  namespace: "Namespace",
  cpuCores: "CPU quota (cores)",
  memoryGi: "Memory quota (GiB)",
};

export const justificationSchema = z
  .string()
  .trim()
  .min(10, "Explain the business need in at least 10 characters.")
  .max(2000, "Use at most 2000 characters.");
