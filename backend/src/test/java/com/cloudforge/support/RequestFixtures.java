package com.cloudforge.support;

import java.util.UUID;

import com.cloudforge.approval.application.ApprovalService;
import com.cloudforge.identity.domain.User;
import com.cloudforge.request.application.ResourceRequestService;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.boot.test.context.TestComponent;

import tools.jackson.databind.json.JsonMapper;

/**
 * Drives requests through the real application services. Integration tests that are about something
 * else (provisioning, notifications) use it to reach a starting state quickly.
 */
@TestComponent
public class RequestFixtures {

    public static final String NAMESPACE_LIMITS = "{\"maxCpuCores\": 8, \"maxMemoryGi\": 32}";

    private final ResourceRequestService requests;
    private final ApprovalService approvals;
    private final ResourceCatalogService catalog;
    private final TestUsers users;
    private final JsonMapper json;

    public RequestFixtures(ResourceRequestService requests, ApprovalService approvals, ResourceCatalogService catalog,
            TestUsers users, JsonMapper json) {
        this.requests = requests;
        this.approvals = approvals;
        this.catalog = catalog;
        this.users = users;
        this.json = json;
    }

    public void allow(User admin, ResourceTypeCode type, Environment environment, String constraints, Integer maxActive) {
        catalog.updatePolicy(admin.getId(), type, environment, true, maxActive, json.readTree(constraints), null);
    }

    public static String namespaceSpec() {
        return "{\"namespace\": \"ns-" + UUID.randomUUID().toString().substring(0, 8) + "\", \"cpuCores\": 2, \"memoryGi\": 4}";
    }

    public ResourceRequest draft(User requester, ResourceTypeCode type, Environment environment, String spec) {
        return requests.create(users.principal(requester), type, environment, json.readTree(spec),
                "Needed for integration testing");
    }

    public ResourceRequest submitted(User requester, ResourceTypeCode type, Environment environment, String spec) {
        ResourceRequest draft = draft(requester, type, environment, spec);
        return requests.submit(users.principal(requester), draft.getId());
    }

    public UUID approved(User requester, User manager, ResourceTypeCode type, Environment environment, String spec) {
        ResourceRequest request = submitted(requester, type, environment, spec);
        approvals.approve(users.principal(manager), request.getId(), "Looks good");
        return request.getId();
    }
}
