package com.cloudforge.provisioning.provider.kubernetes;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.provisioning.provider.ProviderMode;
import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.openapi.models.V1ResourceQuotaSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a namespace and its ResourceQuota through the Kubernetes API (official Java client).
 *
 * <p>Kubernetes object creation is synchronous, so {@link #start} completes in one call. Ownership is
 * recorded with the {@code cloudforge.io/request-id} label: a retry that finds the namespace already
 * present continues if the label matches (a previous attempt created it) and fails permanently if it
 * does not, so CloudForge never adopts a namespace it did not create.
 */
public class KubernetesProvisioningAdapter implements CloudProvisioningProvider {

    static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
    static final String REQUEST_ID_LABEL = "cloudforge.io/request-id";
    static final String ENVIRONMENT_LABEL = "cloudforge.io/environment";
    static final String REQUEST_NUMBER_ANNOTATION = "cloudforge.io/request-number";
    static final String QUOTA_NAME = "cloudforge-quota";

    private static final Logger log = LoggerFactory.getLogger(KubernetesProvisioningAdapter.class);

    private final CoreV1Api api;

    public KubernetesProvisioningAdapter(CoreV1Api api) {
        this.api = api;
    }

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.K8S_NAMESPACE;
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.REAL;
    }

    @Override
    public ProvisioningOutcome start(ProvisioningCommand command) {
        String name = command.spec().get("namespace").stringValue();
        try {
            V1Namespace namespace = findNamespace(name);
            if (namespace == null) {
                namespace = createNamespace(name, command);
            }
            if (!isOwnedBy(namespace, command)) {
                return ProvisioningOutcome.Failed.permanent("NAMESPACE_TAKEN",
                        "Namespace '" + name + "' already exists and was not created for this request.");
            }
            ensureQuota(name, command);
            return new ProvisioningOutcome.Completed(namespace.getMetadata().getUid(), Map.of(
                    "namespace", name,
                    "cpuCores", cpu(command).toPlainString(),
                    "memoryGi", memoryGi(command).toPlainString()));
        } catch (ApiException e) {
            return classify(e, name);
        }
    }

    /** Namespace creation is synchronous; there is never an operation to poll. */
    @Override
    public ProvisioningOutcome poll(ProvisioningCommand command, String operationId) {
        return start(command);
    }

    private V1Namespace findNamespace(String name) throws ApiException {
        try {
            return api.readNamespace(name).execute();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private V1Namespace createNamespace(String name, ProvisioningCommand command) throws ApiException {
        V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta()
                .name(name)
                .putLabelsItem(MANAGED_BY_LABEL, "cloudforge")
                .putLabelsItem(REQUEST_ID_LABEL, command.requestId().toString())
                .putLabelsItem(ENVIRONMENT_LABEL, command.environment().name().toLowerCase(Locale.ROOT))
                .putAnnotationsItem(REQUEST_NUMBER_ANNOTATION, command.requestNumber()));
        try {
            V1Namespace created = api.createNamespace(namespace).fieldManager("cloudforge").execute();
            log.info("Created namespace {} for request {}", name, command.requestNumber());
            return created;
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                // Created concurrently (or by an earlier attempt whose response was lost): re-read and let
                // the ownership check decide.
                return api.readNamespace(name).execute();
            }
            throw e;
        }
    }

    private void ensureQuota(String namespace, ProvisioningCommand command) throws ApiException {
        String cpu = cpu(command).toPlainString();
        String memory = memoryGi(command).toPlainString() + "Gi";
        V1ResourceQuota quota = new V1ResourceQuota()
                .metadata(new V1ObjectMeta().name(QUOTA_NAME)
                        .putLabelsItem(MANAGED_BY_LABEL, "cloudforge")
                        .putLabelsItem(REQUEST_ID_LABEL, command.requestId().toString()))
                .spec(new V1ResourceQuotaSpec()
                        .putHardItem("requests.cpu", new Quantity(cpu))
                        .putHardItem("limits.cpu", new Quantity(cpu))
                        .putHardItem("requests.memory", new Quantity(memory))
                        .putHardItem("limits.memory", new Quantity(memory)));
        try {
            api.createNamespacedResourceQuota(namespace, quota).fieldManager("cloudforge").execute();
        } catch (ApiException e) {
            if (e.getCode() != 409) { // 409: created by an earlier attempt for this request
                throw e;
            }
        }
    }

    private static boolean isOwnedBy(V1Namespace namespace, ProvisioningCommand command) {
        Map<String, String> labels = namespace.getMetadata() == null ? null : namespace.getMetadata().getLabels();
        return labels != null && command.requestId().toString().equals(labels.get(REQUEST_ID_LABEL));
    }

    /**
     * Maps API errors to retryable or permanent outcomes. The response body is deliberately not
     * copied into the message: it can contain cluster internals.
     */
    static ProvisioningOutcome classify(ApiException e, String namespace) {
        int code = e.getCode();
        if (code == 0) {
            return ProvisioningOutcome.Failed.retryable("PROVIDER_UNREACHABLE", "The Kubernetes API could not be reached.");
        }
        if (code == 429 || code >= 500) {
            return ProvisioningOutcome.Failed.retryable("PROVIDER_UNAVAILABLE",
                    "The Kubernetes API is temporarily unavailable (HTTP " + code + ").");
        }
        if (code == 401 || code == 403) {
            return ProvisioningOutcome.Failed.permanent("ACCESS_DENIED",
                    "CloudForge is not allowed to manage namespaces on this cluster (HTTP " + code + ").");
        }
        if (code == 400 || code == 422) {
            return ProvisioningOutcome.Failed.permanent("INVALID_REQUEST",
                    "The cluster rejected namespace '" + namespace + "' (HTTP " + code + ").");
        }
        return ProvisioningOutcome.Failed.permanent("PROVIDER_ERROR", "Unexpected Kubernetes API response (HTTP " + code + ").");
    }

    private static BigDecimal cpu(ProvisioningCommand command) {
        return command.spec().get("cpuCores").decimalValue().stripTrailingZeros();
    }

    private static BigDecimal memoryGi(ProvisioningCommand command) {
        return command.spec().get("memoryGi").decimalValue().stripTrailingZeros();
    }
}
