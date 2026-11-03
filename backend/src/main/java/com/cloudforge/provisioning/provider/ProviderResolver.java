package com.cloudforge.provisioning.provider;

import java.util.List;
import java.util.Optional;

import com.cloudforge.provisioning.ProvisioningProperties;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Picks the adapter for a resource type according to the configured {@link ProviderMode}. Startup
 * fails if a mode is configured without a matching adapter, so a typo cannot silently fall back.
 */
@Component
public class ProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(ProviderResolver.class);

    private final List<CloudProvisioningProvider> providers;
    private final ProvisioningProperties properties;

    public ProviderResolver(List<CloudProvisioningProvider> providers, ProvisioningProperties properties) {
        this.providers = List.copyOf(providers);
        this.properties = properties;
        for (ResourceTypeCode type : ResourceTypeCode.values()) {
            ProviderMode mode = properties.modeFor(type);
            if (mode != ProviderMode.DISABLED && find(type, mode).isEmpty()) {
                throw new IllegalStateException("No " + mode + " provisioning provider available for " + type);
            }
            if (mode == ProviderMode.REAL) {
                log.warn("REAL provisioning is enabled for {}: approved requests create actual cloud resources", type);
            }
        }
    }

    /** Empty when provisioning for this type is disabled. */
    public Optional<CloudProvisioningProvider> resolve(ResourceTypeCode type) {
        ProviderMode mode = properties.modeFor(type);
        return mode == ProviderMode.DISABLED ? Optional.empty() : find(type, mode);
    }

    private Optional<CloudProvisioningProvider> find(ResourceTypeCode type, ProviderMode mode) {
        return providers.stream().filter(p -> p.type() == type && p.mode() == mode).findFirst();
    }
}
