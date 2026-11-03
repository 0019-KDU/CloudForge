# ADR-0008: Real cloud provisioning only on explicit per-provider opt-in

**Status:** Accepted · 2026-10-30

## Context
CloudForge creates billable resources: AWS accounts (which are hard to close), GCP projects (whose IDs can never be reused), and cluster namespaces. Development and automated tests must never create them by accident, while production must use the real provider APIs.

## Decision
- Each provider has a mode: `disabled` (the default in every profile), `mock` (simulated), or `real`. Real adapters and their SDK clients are Spring beans **only** when `cloudforge.provisioning.<provider>.mode=real`; they do not exist otherwise (`RealAdapterWiringTest`).
- `ProviderResolver` fails startup if a configured mode has no matching adapter and logs a warning for every provider running in `real` mode.
- The worker itself is off unless `cloudforge.provisioning.worker.enabled=true`. The `local` profile enables it with every provider in `mock`.
- Credentials come only from the platform: the AWS default chain (IAM role, IRSA, Pod Identity), GCP Application Default Credentials (Workload Identity), and the Kubernetes in-cluster service account or kubeconfig. CloudForge stores no credentials in its database or configuration.
- Integration tests replace the resolver with a scripted fake. Real adapters are unit-tested against fakes: an in-process HTTP server for Kubernetes, a gateway fake with real proto types for GCP, and a mocked SDK client interface for AWS.
- Execution-time safety: the spec is re-validated against the **current** policy immediately before the first provider call, and only requests in APPROVED or PROVISIONING are ever executed.

## Consequences
- No accidental cloud spend from development or CI.
- Real-provider behaviour (exact error codes, eventual consistency, quotas) is verified only by a manually run sandbox test in a dedicated account, organization or folder. This is not automated and is deliberately not part of `mvn verify`.
