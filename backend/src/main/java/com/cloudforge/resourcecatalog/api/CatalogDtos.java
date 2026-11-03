package com.cloudforge.resourcecatalog.api;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.cloudforge.resourcecatalog.application.ResourceCatalogService.CatalogEntry;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService.PolicyView;
import com.cloudforge.resourcecatalog.domain.CloudProvider;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceType;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import tools.jackson.databind.JsonNode;

/** Request and response bodies of the catalog API. */
final class CatalogDtos {

    private CatalogDtos() {
    }

    /**
     * What any authenticated user sees: where the type may be requested and, per allowed environment,
     * the limits a request must respect (approved OUs or folders, CPU and memory caps), so request forms
     * can offer valid choices instead of guessing. Denied environments are omitted.
     */
    record ResourceTypeResponse(ResourceTypeCode code, CloudProvider provider, String displayName, String description,
            List<Environment> allowedEnvironments, List<EnvironmentPolicy> environments) {

        static ResourceTypeResponse from(CatalogEntry entry) {
            ResourceType type = entry.type();
            return new ResourceTypeResponse(type.getCode(), type.getProvider(), type.getDisplayName(),
                    type.getDescription(), entry.allowedEnvironments(),
                    entry.policies().values().stream().filter(PolicyView::allowed)
                            .map(p -> new EnvironmentPolicy(p.environment(), p.maxActivePerUser(), p.constraints()))
                            .toList());
        }
    }

    record EnvironmentPolicy(Environment environment, Integer maxActivePerUser, JsonNode constraints) {
    }

    record AdminResourceTypeResponse(ResourceTypeCode code, CloudProvider provider, String displayName,
            String description, boolean enabled, Long version, List<PolicyResponse> policies) {

        static AdminResourceTypeResponse from(CatalogEntry entry) {
            ResourceType type = entry.type();
            return new AdminResourceTypeResponse(type.getCode(), type.getProvider(), type.getDisplayName(),
                    type.getDescription(), type.isEnabled(), type.getVersion(),
                    entry.policies().values().stream().map(PolicyResponse::from).toList());
        }
    }

    /** {@code version} is null when the environment has no stored policy yet. */
    record PolicyResponse(Environment environment, boolean allowed, Integer maxActivePerUser, JsonNode constraints,
            Long version) {

        static PolicyResponse from(PolicyView view) {
            return new PolicyResponse(view.environment(), view.allowed(), view.maxActivePerUser(), view.constraints(),
                    view.version());
        }
    }

    record UpdateResourceTypeRequest(@NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 1000) String description,
            @NotNull Boolean enabled) {
    }

    record UpdatePolicyRequest(@NotNull Boolean allowed, @Positive Integer maxActivePerUser, JsonNode constraints) {
    }

    record SpecValidationRequest(@NotNull Environment environment, @NotNull JsonNode spec) {
    }

    record SpecValidationResponse(boolean valid) {
    }
}
