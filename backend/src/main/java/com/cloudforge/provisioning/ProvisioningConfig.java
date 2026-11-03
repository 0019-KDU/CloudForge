package com.cloudforge.provisioning;

import java.util.random.RandomGenerator;

import com.cloudforge.provisioning.domain.RetryPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProvisioningConfig {

    @Bean
    RetryPolicy provisioningRetryPolicy(ProvisioningProperties properties) {
        ProvisioningProperties.Retry retry = properties.retry();
        return new RetryPolicy(retry.maxAttempts(), retry.baseDelay(), retry.maxDelay(), RandomGenerator.getDefault());
    }
}
