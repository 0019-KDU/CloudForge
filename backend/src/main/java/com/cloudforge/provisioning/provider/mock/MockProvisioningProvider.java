package com.cloudforge.provisioning.provider.mock;

import java.time.Duration;
import java.util.Map;

import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.provisioning.provider.ProviderMode;
import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

/**
 * Simulated provider for development: behaves like a long-running cloud operation (accepted on
 * start, completed on the first poll) without contacting any cloud. Deterministic and idempotent:
 * the same command always yields the same identifiers.
 */
public class MockProvisioningProvider implements CloudProvisioningProvider {

    static final Duration SIMULATED_POLL_DELAY = Duration.ofSeconds(5);

    private final ResourceTypeCode type;

    public MockProvisioningProvider(ResourceTypeCode type) {
        this.type = type;
    }

    @Override
    public ResourceTypeCode type() {
        return type;
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.MOCK;
    }

    @Override
    public ProvisioningOutcome start(ProvisioningCommand command) {
        return new ProvisioningOutcome.InProgress("mock-op-" + command.idempotencyKey(), SIMULATED_POLL_DELAY);
    }

    @Override
    public ProvisioningOutcome poll(ProvisioningCommand command, String operationId) {
        return new ProvisioningOutcome.Completed("mock-" + type.name().toLowerCase() + "-" + command.requestId(),
                Map.of("simulated", "true", "requestNumber", command.requestNumber()));
    }
}
