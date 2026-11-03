# ADR-0006: Resource types in code, specifications as validated JSONB

**Status:** Accepted · 2026-10-30

## Context
Each provider needs different input: an AWS account needs an email and an organizational unit, a GCP project a project ID and folder, a namespace CPU and memory. Admins must be able to restrict where resources go (approved OUs and folders, quota caps) per environment. New types will be added rarely, and each needs a provisioning adapter anyway.

## Decision
- **Types are an enum plus seeded rows.** `ResourceTypeCode` defines the types. Rows in `resource_type` (seeded by migration) let admins rename, describe and enable or disable them. Types cannot be created through the API: a type without a validator and adapter would be unusable.
- **One `ResourceSpecValidator` per type,** written in Java, not JSON Schema. The rules go beyond structure: reserved names (`kube-*`, `default`), cross-field and policy checks (OU in the allowed list, CPU under the cap), and provider naming rules. Validators reject unknown fields and report every violation with its path (`spec.namespace`). Startup fails if any type lacks a validator.
- **Policies per (type, environment),** with type-specific limits as a JSONB `constraints` object. **Deny by default:** a missing policy, or `allowed = false`, blocks requests. An *allowing* policy must name where resources may be placed (OUs, folders) or set quota caps. "Anything goes" is never implicit.
- Request specs are stored as JSONB (phase 7) and checked by `ResourceCatalogService.checkRequestable` on submit **and again before provisioning**, because policies can tighten in between.

## Consequences
- Adding a type takes: an enum value, a validator, a provisioning adapter, and a seed migration.
- JSONB keeps the schema stable across types. Validation in code is the single source of truth, so data that is not validated never reaches a provider.
- Provider-side uniqueness (globally unique GCP project IDs, AWS root emails) can only be checked at provisioning time and surfaces as a provisioning failure.
