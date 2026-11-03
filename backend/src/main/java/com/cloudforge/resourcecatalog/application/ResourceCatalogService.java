package com.cloudforge.resourcecatalog.application;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cloudforge.audit.application.AuditLog;
import com.cloudforge.audit.domain.Actor;
import com.cloudforge.audit.domain.AuditAction;
import com.cloudforge.audit.domain.AuditEntityType;
import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.error.CloudForgeException;
import com.cloudforge.common.error.FieldViolation;
import com.cloudforge.common.error.InvalidSpecificationException;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourcePolicy;
import com.cloudforge.resourcecatalog.domain.ResourceType;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;
import com.cloudforge.resourcecatalog.repository.ResourcePolicyRepository;
import com.cloudforge.resourcecatalog.repository.ResourceTypeRepository;
import com.cloudforge.resourcecatalog.spec.ResourceSpecValidator;
import com.cloudforge.resourcecatalog.spec.ResourceSpecValidators;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The catalog of requestable resources and the governance rules for them. Also the public entry
 * point other modules use to check a resource specification: the request module calls
 * {@link #checkRequestable} on submission, and provisioning calls it again before execution, so a
 * policy tightened in between still applies.
 */
@Service
// Business exceptions from checks (invalid spec, environment not allowed) are expected and often caught
// by the caller, for example the provisioning worker recording a POLICY_VIOLATION. Without
// noRollbackFor they would mark the caller's whole transaction rollback-only.
@Transactional(readOnly = true, noRollbackFor = CloudForgeException.class)
public class ResourceCatalogService {

    private final ResourceTypeRepository types;
    private final ResourcePolicyRepository policies;
    private final ResourceSpecValidators validators;
    private final JsonMapper jsonMapper;
    private final AuditLog audit;

    public ResourceCatalogService(ResourceTypeRepository types, ResourcePolicyRepository policies,
            ResourceSpecValidators validators, JsonMapper jsonMapper, AuditLog audit) {
        this.types = types;
        this.policies = policies;
        this.validators = validators;
        this.jsonMapper = jsonMapper;
        this.audit = audit;
    }

    /** A type together with its policy for every environment (missing rows are reported as deny). */
    public record CatalogEntry(ResourceType type, Map<Environment, PolicyView> policies) {

        public List<Environment> allowedEnvironments() {
            return policies.values().stream().filter(PolicyView::allowed).map(PolicyView::environment).toList();
        }
    }

    /** {@code version} is null for environments that have no stored policy yet (implicit deny). */
    public record PolicyView(Environment environment, boolean allowed, Integer maxActivePerUser, JsonNode constraints,
            Long version) {
    }

    /** The policy that applies to an accepted request, for checks owned by other modules (quotas). */
    public record RequestablePolicy(ResourceType type, Environment environment, Integer maxActivePerUser) {
    }

    public List<CatalogEntry> listCatalog(boolean includeDisabled) {
        List<ResourceType> list = includeDisabled
                ? types.findAllByOrderByDisplayNameAsc()
                : types.findByEnabledTrueOrderByDisplayNameAsc();
        Map<UUID, List<ResourcePolicy>> policiesByType = policies
                .findByResourceTypeIdIn(list.stream().map(ResourceType::getId).toList()).stream()
                .collect(Collectors.groupingBy(ResourcePolicy::getResourceTypeId));
        List<CatalogEntry> entries = new ArrayList<>();
        for (ResourceType type : list) {
            entries.add(new CatalogEntry(type, policyViews(policiesByType.getOrDefault(type.getId(), List.of()))));
        }
        return entries;
    }

    public CatalogEntry getEntry(ResourceTypeCode code, boolean includeDisabled) {
        ResourceType type = requireType(code);
        if (!includeDisabled && !type.isEnabled()) {
            throw typeNotFound();
        }
        return new CatalogEntry(type, policyViews(policies.findByResourceTypeIdIn(List.of(type.getId()))));
    }

    @Transactional
    public ResourceType updateType(UUID actorId, ResourceTypeCode code, String displayName, String description,
            boolean enabled, Long expectedVersion) {
        ResourceType type = requireType(code);
        type.requireVersion(expectedVersion);
        type.describe(displayName, description);
        type.setEnabled(enabled);
        audit.record(Actor.user(actorId), AuditAction.RESOURCE_TYPE_UPDATED, AuditEntityType.RESOURCE_TYPE,
                type.getId(), Map.of("code", code, "enabled", enabled));
        return type;
    }

    /** Creates or replaces the policy for one environment after validating its constraints. */
    @Transactional
    public PolicyView updatePolicy(UUID actorId, ResourceTypeCode code, Environment environment, boolean allowed,
            Integer maxActivePerUser, JsonNode constraints, Long expectedVersion) {
        ResourceType type = requireType(code);
        JsonNode effective = constraints == null || constraints.isNull() ? jsonMapper.createObjectNode() : constraints;
        List<FieldViolation> violations = validators.forType(code).validateConstraints(effective, allowed);
        if (!violations.isEmpty()) {
            throw new InvalidSpecificationException("INVALID_POLICY_CONSTRAINTS",
                    "The policy constraints are invalid for " + code + ".", violations);
        }
        ResourcePolicy policy = policies.findByResourceTypeIdAndEnvironment(type.getId(), environment)
                .orElseGet(() -> new ResourcePolicy(type.getId(), environment));
        policy.requireVersion(expectedVersion); // a new policy has no version, so any If-Match fails
        policy.update(allowed, maxActivePerUser, jsonMapper.writeValueAsString(effective));
        ResourcePolicy saved = policies.saveAndFlush(policy); // flush so the returned version is current
        Map<String, Object> details = new HashMap<>();
        details.put("code", code);
        details.put("environment", environment);
        details.put("allowed", allowed);
        details.put("maxActivePerUser", maxActivePerUser);
        details.put("constraints", effective);
        audit.record(Actor.user(actorId), AuditAction.RESOURCE_POLICY_UPDATED, AuditEntityType.RESOURCE_TYPE,
                type.getId(), details);
        return toView(saved);
    }

    /**
     * Verifies that {@code spec} may be requested for {@code code} in {@code environment}: the type
     * is enabled, the environment is allowed, and the spec satisfies the type's rules and the policy.
     */
    public RequestablePolicy checkRequestable(ResourceTypeCode code, Environment environment, JsonNode spec) {
        ResourceType type = requireType(code);
        if (!type.isEnabled()) {
            throw new BusinessRuleViolationException("RESOURCE_TYPE_DISABLED", "This resource type is currently unavailable.");
        }
        ResourcePolicy policy = policies.findByResourceTypeIdAndEnvironment(type.getId(), environment)
                .filter(ResourcePolicy::isAllowed)
                .orElseThrow(() -> new BusinessRuleViolationException("ENVIRONMENT_NOT_ALLOWED",
                        "This resource type cannot be requested for the " + environment + " environment."));
        ResourceSpecValidator validator = validators.forType(code);
        List<FieldViolation> violations = validator.validateSpec(spec, parse(policy.getConstraintsJson()));
        if (!violations.isEmpty()) {
            throw new InvalidSpecificationException("INVALID_RESOURCE_SPEC",
                    "The resource specification is invalid.", violations);
        }
        return new RequestablePolicy(type, environment, policy.getMaxActivePerUser());
    }

    public ResourceType requireType(ResourceTypeCode code) {
        return types.findByCode(code).orElseThrow(ResourceCatalogService::typeNotFound);
    }

    private Map<Environment, PolicyView> policyViews(List<ResourcePolicy> typePolicies) {
        Map<Environment, PolicyView> views = new EnumMap<>(Environment.class);
        for (Environment environment : Environment.values()) {
            views.put(environment, new PolicyView(environment, false, null, jsonMapper.createObjectNode(), null));
        }
        for (ResourcePolicy policy : typePolicies) {
            views.put(policy.getEnvironment(), toView(policy));
        }
        return views;
    }

    private PolicyView toView(ResourcePolicy policy) {
        return new PolicyView(policy.getEnvironment(), policy.isAllowed(), policy.getMaxActivePerUser(),
                parse(policy.getConstraintsJson()), policy.getVersion());
    }

    private JsonNode parse(String json) {
        return jsonMapper.readTree(json);
    }

    private static NotFoundException typeNotFound() {
        return new NotFoundException("RESOURCE_TYPE_NOT_FOUND", "Resource type was not found.");
    }
}
