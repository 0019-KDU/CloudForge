package com.cloudforge.provisioning.provider;

/**
 * How a resource type is provisioned. The default is {@link #DISABLED} in every profile: real cloud
 * calls happen only when {@code cloudforge.provisioning.<provider>.mode=real} is set explicitly.
 */
public enum ProviderMode {
    /** Jobs fail permanently with PROVIDER_DISABLED (and can be retried once a mode is configured). */
    DISABLED,
    /** Simulated provider: never touches a cloud. For development and demos. */
    MOCK,
    /** Real SDK adapter. Creates billable resources. */
    REAL
}
