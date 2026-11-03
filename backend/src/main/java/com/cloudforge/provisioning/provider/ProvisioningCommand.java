package com.cloudforge.provisioning.provider;

import java.util.UUID;

import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import tools.jackson.databind.JsonNode;

/**
 * Everything an adapter needs to create one resource. The spec has already passed the type's
 * {@code ResourceSpecValidator} against the current policy.
 *
 * @param idempotencyKey stable for the request across retries and worker crashes. Adapters attach
 *                       it to the provider resource (label or tag) to recognise their own earlier attempts.
 */
public record ProvisioningCommand(UUID requestId, String requestNumber, ResourceTypeCode type, Environment environment,
        JsonNode spec, String idempotencyKey) {
}
