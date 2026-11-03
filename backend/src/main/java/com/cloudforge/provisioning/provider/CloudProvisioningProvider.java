package com.cloudforge.provisioning.provider;

import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

/**
 * Port to a cloud provider for one resource type (Adapter pattern). The application layer depends
 * only on this interface; SDK details stay inside the adapter.
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li><b>Idempotent:</b> {@link #start} may be called again for the same command after a crash or a
 *       retry. It must detect a resource it created earlier (via {@code idempotencyKey}) and report
 *       it instead of creating a duplicate, and refuse to adopt a same-named resource it did not create.</li>
 *   <li><b>Bounded:</b> every SDK call has timeouts well below the worker lease (5 minutes by default).</li>
 *   <li><b>Never throws for expected provider errors:</b> they become {@link ProvisioningOutcome.Failed},
 *       with retryable versus permanent decided by the adapter, which knows the provider's error model.</li>
 * </ul>
 */
public interface CloudProvisioningProvider {

    ResourceTypeCode type();

    ProviderMode mode();

    ProvisioningOutcome start(ProvisioningCommand command);

    /** Checks a long-running operation previously returned as {@link ProvisioningOutcome.InProgress}. */
    ProvisioningOutcome poll(ProvisioningCommand command, String operationId);
}
