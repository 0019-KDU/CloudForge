package com.cloudforge.provisioning.provider.aws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The real AWS adapter (and its SDK client) is created only on explicit opt-in. Building the client
 * does not contact AWS or resolve credentials, so this test is safe offline.
 */
class RealAdapterWiringTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AwsProviderConfig.class);

    @Test
    void absentByDefault() {
        context.run(ctx -> assertThat(ctx).doesNotHaveBean(AwsProvisioningAdapter.class));
    }

    @Test
    void absentInMockMode() {
        context.withPropertyValues("cloudforge.provisioning.aws.mode=mock")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AwsProvisioningAdapter.class));
    }

    @Test
    void presentOnlyWhenRealModeIsSetExplicitly() {
        context.withPropertyValues("cloudforge.provisioning.aws.mode=real")
                .run(ctx -> assertThat(ctx).hasSingleBean(AwsProvisioningAdapter.class));
    }
}
