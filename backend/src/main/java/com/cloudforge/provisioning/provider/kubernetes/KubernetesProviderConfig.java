package com.cloudforge.provisioning.provider.kubernetes;

import java.io.IOException;
import java.time.Duration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Real Kubernetes adapter, created only with {@code cloudforge.provisioning.kubernetes.mode=real}.
 *
 * <p>Credentials are never configured in CloudForge itself: with no kubeconfig path the client uses
 * the standard resolution ({@code KUBECONFIG}, {@code ~/.kube/config}, or the in-cluster service
 * account). Grant that identity only {@code create}/{@code get} on namespaces and resourcequotas.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cloudforge.provisioning.kubernetes", name = "mode", havingValue = "real")
@EnableConfigurationProperties(KubernetesProviderConfig.KubernetesProperties.class)
class KubernetesProviderConfig {

    /**
     * @param kubeconfig optional path to a kubeconfig file; empty means default resolution
     */
    @ConfigurationProperties("cloudforge.provisioning.kubernetes")
    record KubernetesProperties(String kubeconfig, @DefaultValue("PT10S") Duration connectTimeout,
            @DefaultValue("PT30S") Duration readTimeout) {
    }

    @Bean
    KubernetesProvisioningAdapter kubernetesProvisioningAdapter(KubernetesProperties properties) throws IOException {
        ApiClient client = properties.kubeconfig() == null || properties.kubeconfig().isBlank()
                ? Config.defaultClient()
                : Config.fromConfig(properties.kubeconfig());
        client.setConnectTimeout((int) properties.connectTimeout().toMillis());
        client.setReadTimeout((int) properties.readTimeout().toMillis());
        return new KubernetesProvisioningAdapter(new CoreV1Api(client));
    }
}
