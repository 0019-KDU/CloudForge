package com.cloudforge.resourcecatalog.spec;

import java.util.List;

import com.cloudforge.common.error.FieldViolation;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import tools.jackson.databind.JsonNode;

/**
 * Validation for one resource type: the shape and naming rules of its specification, and the
 * policy constraints an administrator may set for it. One implementation per type; the provisioning
 * adapter for that type may rely on a spec having passed {@link #validateSpec}.
 *
 * <p>Returned violations use the paths {@code spec.<field>} and {@code constraints.<field>}.
 */
public interface ResourceSpecValidator {

    ResourceTypeCode type();

    /**
     * @param constraints the policy constraints, already validated
     */
    List<FieldViolation> validateSpec(JsonNode spec, JsonNode constraints);

    /**
     * @param allowed whether the policy allows requests. An allowing policy must restrict where
     *                resources may be placed, because there is no safe implicit default.
     */
    List<FieldViolation> validateConstraints(JsonNode constraints, boolean allowed);
}
