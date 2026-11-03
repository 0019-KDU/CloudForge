package com.cloudforge.provisioning.provider.mock;

import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mock providers always exist; they are used only for types whose mode is {@code mock}. */
@Configuration(proxyBeanMethods = false)
class MockProviderConfig {

    @Bean
    CloudProvisioningProvider mockAwsAccountProvider() {
        return new MockProvisioningProvider(ResourceTypeCode.AWS_ACCOUNT);
    }

    @Bean
    CloudProvisioningProvider mockGcpProjectProvider() {
        return new MockProvisioningProvider(ResourceTypeCode.GCP_PROJECT);
    }

    @Bean
    CloudProvisioningProvider mockKubernetesNamespaceProvider() {
        return new MockProvisioningProvider(ResourceTypeCode.K8S_NAMESPACE);
    }
}
