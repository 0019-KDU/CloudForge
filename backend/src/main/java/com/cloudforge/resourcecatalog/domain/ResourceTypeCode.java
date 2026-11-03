package com.cloudforge.resourcecatalog.domain;

/**
 * The resource types CloudForge can provision. A new type needs code (a spec validator and a
 * provisioning adapter) plus a seed migration, so the set is an enum rather than free data.
 */
public enum ResourceTypeCode {
    AWS_ACCOUNT(CloudProvider.AWS),
    GCP_PROJECT(CloudProvider.GCP),
    K8S_NAMESPACE(CloudProvider.KUBERNETES);

    private final CloudProvider provider;

    ResourceTypeCode(CloudProvider provider) {
        this.provider = provider;
    }

    public CloudProvider provider() {
        return provider;
    }
}
