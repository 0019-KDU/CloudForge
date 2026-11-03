import { describe, expect, it } from "vitest";

import type { EnvironmentPolicy } from "@/types/api";

import { resourceName, specSchema, toSpec } from "./spec";

const awsPolicy: EnvironmentPolicy = {
  environment: "DEVELOPMENT",
  maxActivePerUser: null,
  constraints: { allowedOrganizationalUnitIds: ["ou-ab12-cd34ef56"], allowedEmailDomains: ["acme.com"] },
};

describe("request specs (mirrors of the backend validators)", () => {
  it("accepts a valid AWS account within policy", () => {
    const result = specSchema("AWS_ACCOUNT", awsPolicy).safeParse({
      accountName: "payments-dev",
      email: "aws-payments-dev@acme.com",
      organizationalUnitId: "ou-ab12-cd34ef56",
    });
    expect(result.success).toBe(true);
  });

  it("rejects AWS emails outside the approved domains and unapproved OUs", () => {
    const result = specSchema("AWS_ACCOUNT", awsPolicy).safeParse({
      accountName: "payments-dev",
      email: "someone@gmail.com",
      organizationalUnitId: "ou-zz99-zzzzzzzz",
    });
    expect(result.success).toBe(false);
    const fields = result.error?.issues.map((issue) => issue.path.join("."));
    expect(fields).toEqual(expect.arrayContaining(["email", "organizationalUnitId"]));
  });

  it("applies GCP project ID rules", () => {
    const schema = specSchema("GCP_PROJECT", { environment: "DEVELOPMENT", maxActivePerUser: null, constraints: { allowedFolderIds: ["123"] } });
    expect(schema.safeParse({ projectId: "acme-payments", displayName: "", folderId: "123" }).success).toBe(true);
    expect(schema.safeParse({ projectId: "my-google-app", displayName: "", folderId: "123" }).success).toBe(false);
    expect(schema.safeParse({ projectId: "Acme", displayName: "", folderId: "123" }).success).toBe(false);
  });

  it("converts form strings to the JSON the API expects", () => {
    expect(toSpec("K8S_NAMESPACE", { namespace: " team-a ", cpuCores: "0.5", memoryGi: "2" })).toEqual({
      namespace: "team-a",
      cpuCores: 0.5,
      memoryGi: 2,
    });
    // Optional fields are omitted rather than sent empty (the backend rejects unknown/invalid values).
    expect(toSpec("GCP_PROJECT", { projectId: "acme-dev", displayName: "", folderId: "1" })).toEqual({ projectId: "acme-dev", folderId: "1" });
  });

  it("names a resource by its identifying field", () => {
    expect(resourceName("GCP_PROJECT", { projectId: "acme-dev" })).toBe("acme-dev");
    expect(resourceName("K8S_NAMESPACE", {})).toBeNull();
  });
});
