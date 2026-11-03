package com.cloudforge.provisioning.provider.gcp;

import java.io.IOException;

import com.google.cloud.resourcemanager.v3.ProjectsClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Real GCP adapter, created only with {@code cloudforge.provisioning.gcp.mode=real}.
 *
 * <p>Authentication uses Application Default Credentials (Workload Identity, an attached service
 * account, or {@code GOOGLE_APPLICATION_CREDENTIALS} on a workstation). No key material is configured
 * in CloudForge. Grant the identity {@code roles/resourcemanager.projectCreator} on the approved
 * folders only. The client's default per-call timeouts (well under the worker lease) apply.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cloudforge.provisioning.gcp", name = "mode", havingValue = "real")
class GcpProviderConfig {

    @Bean
    ResourceManagerGateway resourceManagerGateway() throws IOException {
        return new ResourceManagerGateway(ProjectsClient.create());
    }

    @Bean
    GcpProvisioningAdapter gcpProvisioningAdapter(ResourceManagerGateway gateway) {
        return new GcpProvisioningAdapter(gateway);
    }
}
