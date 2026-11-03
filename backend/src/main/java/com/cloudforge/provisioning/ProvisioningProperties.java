package com.cloudforge.provisioning;

import java.time.Duration;

import com.cloudforge.provisioning.provider.ProviderMode;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code cloudforge.provisioning}. Safe defaults: the worker is off and every
 * provider is {@code DISABLED}; real provisioning needs explicit configuration per provider.
 *
 * @param operationTimeout how long a long-running provider operation may take before the job fails
 */
@ConfigurationProperties("cloudforge.provisioning")
public record ProvisioningProperties(
        @DefaultValue Worker worker,
        @DefaultValue Retry retry,
        @DefaultValue("PT2H") Duration operationTimeout,
        @DefaultValue Provider aws,
        @DefaultValue Provider gcp,
        @DefaultValue Provider kubernetes) {

    /**
     * @param lease how long a claimed job stays locked to one worker. Must exceed the longest provider
     *              call; an expired lease lets another instance recover the job.
     */
    public record Worker(@DefaultValue("false") boolean enabled, @DefaultValue("PT10S") Duration pollInterval,
            @DefaultValue("10") int batchSize, @DefaultValue("PT5M") Duration lease) {
    }

    /** Exponential backoff with jitter for retryable failures of the start call. */
    public record Retry(@DefaultValue("5") int maxAttempts, @DefaultValue("PT30S") Duration baseDelay,
            @DefaultValue("PT15M") Duration maxDelay) {
    }

    public record Provider(@DefaultValue("disabled") ProviderMode mode) {
    }

    public ProviderMode modeFor(ResourceTypeCode type) {
        return switch (type.provider()) {
            case AWS -> aws.mode();
            case GCP -> gcp.mode();
            case KUBERNETES -> kubernetes.mode();
        };
    }
}
