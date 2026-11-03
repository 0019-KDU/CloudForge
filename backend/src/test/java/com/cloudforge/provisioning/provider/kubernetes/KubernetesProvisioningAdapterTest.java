package com.cloudforge.provisioning.provider.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Completed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Failed;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real Kubernetes Java client against an in-process HTTP server that plays the API server, so
 * request paths, payloads and error handling are exercised without a cluster.
 */
class KubernetesProvisioningAdapterTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final UUID requestId = UUID.randomUUID();
    private MockWebServer server;
    private KubernetesProvisioningAdapter adapter;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        ApiClient client = Config.fromUrl(baseUrl.substring(0, baseUrl.length() - 1), false);
        client.setReadTimeout(2_000);
        adapter = new KubernetesProvisioningAdapter(new CoreV1Api(client));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private ProvisioningCommand command() {
        return new ProvisioningCommand(requestId, "CF-2026-000042", ResourceTypeCode.K8S_NAMESPACE, Environment.STAGING,
                json.readTree("{\"namespace\": \"team-a\", \"cpuCores\": 1.5, \"memoryGi\": 4}"), "cloudforge-" + requestId);
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build();
    }

    private String namespace(String requestIdLabel) {
        return "{\"apiVersion\":\"v1\",\"kind\":\"Namespace\",\"metadata\":{\"name\":\"team-a\",\"uid\":\"uid-123\","
                + "\"labels\":{\"cloudforge.io/request-id\":\"" + requestIdLabel + "\"}}}";
    }

    private static final String NOT_FOUND = "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"status\":\"Failure\",\"code\":404}";
    private static final String QUOTA = "{\"apiVersion\":\"v1\",\"kind\":\"ResourceQuota\",\"metadata\":{\"name\":\"cloudforge-quota\"}}";

    @Test
    void createsLabelledNamespaceAndQuota() throws Exception {
        server.enqueue(json(404, NOT_FOUND));
        server.enqueue(json(201, namespace(requestId.toString())));
        server.enqueue(json(201, QUOTA));

        ProvisioningOutcome outcome = adapter.start(command());

        assertThat(outcome).isInstanceOf(Completed.class);
        assertThat(((Completed) outcome).externalResourceId()).isEqualTo("uid-123");
        assertThat(((Completed) outcome).details()).containsEntry("namespace", "team-a").containsEntry("cpuCores", "1.5");

        RecordedRequest read = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(read.getMethod()).isEqualTo("GET");
        assertThat(read.getTarget()).isEqualTo("/api/v1/namespaces/team-a");

        RecordedRequest create = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(create.getMethod()).isEqualTo("POST");
        assertThat(create.getTarget()).startsWith("/api/v1/namespaces?fieldManager=cloudforge");
        String body = create.getBody().utf8();
        assertThat(body).contains("\"cloudforge.io/request-id\":\"" + requestId + "\"")
                .contains("\"cloudforge.io/environment\":\"staging\"")
                .contains("\"app.kubernetes.io/managed-by\":\"cloudforge\"");

        RecordedRequest quota = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(quota.getTarget()).startsWith("/api/v1/namespaces/team-a/resourcequotas");
        assertThat(quota.getBody().utf8()).contains("\"limits.cpu\":\"1500m\"").contains("\"limits.memory\":\"4Gi\"");
    }

    @Test
    void retryAfterPartialSuccessCompletesWithoutDuplicates() {
        // An earlier attempt created the namespace and quota but its result was never recorded.
        server.enqueue(json(200, namespace(requestId.toString())));
        server.enqueue(json(409, "{\"kind\":\"Status\",\"code\":409,\"reason\":\"AlreadyExists\"}"));

        ProvisioningOutcome outcome = adapter.start(command());

        assertThat(outcome).isInstanceOf(Completed.class);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void neverAdoptsANamespaceCreatedByOthers() {
        server.enqueue(json(200, namespace(UUID.randomUUID().toString())));

        ProvisioningOutcome outcome = adapter.start(command());

        assertThat(outcome).isEqualTo(Failed.permanent("NAMESPACE_TAKEN",
                "Namespace 'team-a' already exists and was not created for this request."));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void concurrentCreationIsResolvedByOwnershipCheck() {
        server.enqueue(json(404, NOT_FOUND));
        server.enqueue(json(409, "{\"kind\":\"Status\",\"code\":409}"));
        server.enqueue(json(200, namespace(requestId.toString())));
        server.enqueue(json(201, QUOTA));

        assertThat(adapter.start(command())).isInstanceOf(Completed.class);
    }

    @Test
    void classifiesErrors() {
        server.enqueue(json(403, "{\"kind\":\"Status\",\"code\":403,\"message\":\"forbidden: system:serviceaccount:x\"}"));
        Failed forbidden = (Failed) adapter.start(command());
        assertThat(forbidden.retryable()).isFalse();
        assertThat(forbidden.code()).isEqualTo("ACCESS_DENIED");
        assertThat(forbidden.message()).doesNotContain("serviceaccount");

        server.enqueue(json(503, "{}"));
        Failed unavailable = (Failed) adapter.start(command());
        assertThat(unavailable.retryable()).isTrue();

        server.enqueue(json(404, NOT_FOUND));
        server.enqueue(json(422, "{\"kind\":\"Status\",\"code\":422}"));
        assertThat(((Failed) adapter.start(command())).code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void unreachableApiServerIsRetryable() {
        server.close();

        Failed failed = (Failed) adapter.start(command());

        assertThat(failed.retryable()).isTrue();
        assertThat(failed.code()).isEqualTo("PROVIDER_UNREACHABLE");
    }
}
