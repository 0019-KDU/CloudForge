package com.cloudforge.provisioning.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.cloudforge.provisioning.ProvisioningProperties;
import com.cloudforge.provisioning.provider.mock.MockProvisioningProvider;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.junit.jupiter.api.Test;

/** Real provisioning must never happen by accident. */
class ProviderSafetyTest {

    private static ProvisioningProperties modes(ProviderMode aws, ProviderMode gcp, ProviderMode kubernetes) {
        return new ProvisioningProperties(
                new ProvisioningProperties.Worker(false, Duration.ofSeconds(10), 10, Duration.ofMinutes(5)),
                new ProvisioningProperties.Retry(5, Duration.ofSeconds(30), Duration.ofMinutes(15)),
                Duration.ofHours(2),
                new ProvisioningProperties.Provider(aws), new ProvisioningProperties.Provider(gcp),
                new ProvisioningProperties.Provider(kubernetes));
    }

    private static List<CloudProvisioningProvider> mocks() {
        return List.of(new MockProvisioningProvider(ResourceTypeCode.AWS_ACCOUNT),
                new MockProvisioningProvider(ResourceTypeCode.GCP_PROJECT),
                new MockProvisioningProvider(ResourceTypeCode.K8S_NAMESPACE));
    }

    @Test
    void disabledIsTheDefaultAndResolvesToNothing() {
        ProviderResolver resolver = new ProviderResolver(mocks(),
                modes(ProviderMode.DISABLED, ProviderMode.DISABLED, ProviderMode.DISABLED));

        for (ResourceTypeCode type : ResourceTypeCode.values()) {
            assertThat(resolver.resolve(type)).isEmpty();
        }
    }

    @Test
    void mockModeNeverResolvesToARealAdapter() {
        ProviderResolver resolver = new ProviderResolver(mocks(),
                modes(ProviderMode.MOCK, ProviderMode.DISABLED, ProviderMode.DISABLED));

        assertThat(resolver.resolve(ResourceTypeCode.AWS_ACCOUNT)).get().extracting(CloudProvisioningProvider::mode)
                .isEqualTo(ProviderMode.MOCK);
    }

    @Test
    void realModeWithoutARealAdapterFailsAtStartup() {
        assertThatThrownBy(() -> new ProviderResolver(mocks(),
                modes(ProviderMode.REAL, ProviderMode.DISABLED, ProviderMode.DISABLED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWS_ACCOUNT");
    }

    @Test
    void mockProviderIsDeterministicAndLongRunning() {
        MockProvisioningProvider mock = new MockProvisioningProvider(ResourceTypeCode.K8S_NAMESPACE);
        var command = new ProvisioningCommand(UUID.randomUUID(), "CF-1", ResourceTypeCode.K8S_NAMESPACE,
                Environment.DEVELOPMENT, null, "key-1");

        assertThat(mock.start(command)).isInstanceOf(ProvisioningOutcome.InProgress.class);
        assertThat(mock.poll(command, "mock-op-key-1")).isEqualTo(mock.poll(command, "mock-op-key-1"));
    }
}
