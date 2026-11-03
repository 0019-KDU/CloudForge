package com.cloudforge.provisioning.provider.aws;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.organizations.OrganizationsClient;

/**
 * Real AWS adapter, created only with {@code cloudforge.provisioning.aws.mode=real}.
 *
 * <p>Credentials come from the SDK default chain (an IAM role for the workload, IRSA or EKS Pod
 * Identity, or a profile on a workstation). Nothing is configured in CloudForge. The identity must
 * act in the organization's management account (or a delegated administrator) with only
 * {@code organizations:CreateAccount}, {@code DescribeCreateAccountStatus}, {@code ListAccounts},
 * {@code ListParents}, {@code ListTagsForResource}, {@code MoveAccount} and {@code TagResource}.
 *
 * @see <a href="https://docs.aws.amazon.com/organizations/latest/APIReference/API_CreateAccount.html">CreateAccount</a>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cloudforge.provisioning.aws", name = "mode", havingValue = "real")
@EnableConfigurationProperties(AwsProviderConfig.AwsProperties.class)
class AwsProviderConfig {

    /**
     * @param region Organizations endpoint region; us-east-1 for the commercial partition
     */
    @ConfigurationProperties("cloudforge.provisioning.aws")
    record AwsProperties(@DefaultValue("us-east-1") String region, @DefaultValue("PT30S") Duration apiCallTimeout,
            @DefaultValue("PT10S") Duration apiCallAttemptTimeout) {
    }

    @Bean(destroyMethod = "close")
    OrganizationsClient organizationsClient(AwsProperties properties) {
        return OrganizationsClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(config -> config
                        .apiCallTimeout(properties.apiCallTimeout())
                        .apiCallAttemptTimeout(properties.apiCallAttemptTimeout()))
                .build();
    }

    @Bean
    AwsProvisioningAdapter awsProvisioningAdapter(OrganizationsClient organizations) {
        return new AwsProvisioningAdapter(organizations);
    }
}
