package com.cloudforge.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cloudforge.approval.application.ApprovalService;
import com.cloudforge.common.error.CloudForgeException;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.repository.UserRepository;
import com.cloudforge.request.application.ResourceRequestService;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;
import com.cloudforge.support.RequestFixtures;
import com.cloudforge.support.TestUsers;
import com.cloudforge.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Request lifecycle, approval and authorization end to end over HTTP with real tokens. This class
 * owns the K8S_NAMESPACE/PRODUCTION and GCP_PROJECT/DEVELOPMENT policies; other integration tests
 * use other type and environment pairs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class, RequestFixtures.class})
class RequestLifecycleIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestUsers testUsers;
    @Autowired
    RequestFixtures fixtures;
    @Autowired
    ResourceRequestService requestService;
    @Autowired
    ApprovalService approvalService;
    @Autowired
    UserRepository users;
    @Autowired
    JdbcTemplate jdbc;

    User admin;
    User manager;
    User engineer;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.PLATFORM_ADMIN);
        manager = testUsers.create(Role.MANAGER);
        engineer = testUsers.createReportingTo(manager, Role.ENGINEER);
        fixtures.allow(admin, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION, RequestFixtures.NAMESPACE_LIMITS, null);
        fixtures.allow(admin, ResourceTypeCode.GCP_PROJECT, Environment.DEVELOPMENT,
                "{\"allowedFolderIds\": [\"1001\"]}", 1);
    }

    // --- happy path ---------------------------------------------------------------------------

    @Test
    void draftSubmitApproveQueuesProvisioningWithFullAuditTrail() throws Exception {
        String id = create(engineer, namespaceBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requestNumber").value(matchesPattern("CF-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.requesterId").value(engineer.getId().toString()))
                .andExpect(jsonPath("$.assignedApproverId").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(body -> JsonPath.read(body, "$.id"));

        action(engineer, id, "submit").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.assignedApproverId").value(manager.getId().toString()));

        mockMvc.perform(get("/api/requests").param("scope", "ASSIGNED").with(testUsers.bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(id)));

        mockMvc.perform(post("/api/requests/{id}/approve", id).with(testUsers.bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\": \"Approved for Q4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.approverId").value(manager.getId().toString()));

        mockMvc.perform(get("/api/requests/{id}", id).with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
        mockMvc.perform(get("/api/requests/{id}/provisioning", id).with(testUsers.bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        mockMvc.perform(get("/api/requests/{id}/audit", id).with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.content[*].action").value(contains(
                        "REQUEST_CREATED", "REQUEST_SUBMITTED", "REQUEST_APPROVED", "PROVISIONING_QUEUED")))
                .andExpect(jsonPath("$.content[2].actorId").value(manager.getId().toString()));
    }

    @Test
    void rejectionNeedsCommentAndNeverCreatesProvisioning() throws Exception {
        String id = submittedNamespace();

        mockMvc.perform(post("/api/requests/{id}/reject", id).with(testUsers.bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\": \" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/requests/{id}/reject", id).with(testUsers.bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\": \"Use the shared namespace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"));

        mockMvc.perform(get("/api/requests/{id}/provisioning", id).with(testUsers.bearer(engineer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROVISIONING_NOT_FOUND"));
        mockMvc.perform(post("/api/requests/{id}/approve", id).with(testUsers.bearer(manager)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provisioning_job WHERE request_id = ?::uuid",
                Long.class, id)).isZero();
    }

    // --- authorization ------------------------------------------------------------------------

    @Test
    void outsidersSeeNotFoundAndNonApproversCannotDecide() throws Exception {
        String id = submittedNamespace();
        User otherEngineer = testUsers.createReportingTo(manager, Role.ENGINEER);
        User otherManager = testUsers.create(Role.MANAGER);

        mockMvc.perform(get("/api/requests/{id}", id).with(testUsers.bearer(otherEngineer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/requests/{id}/approve", id).with(testUsers.bearer(otherManager)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/requests/{id}/cancel", id).with(testUsers.bearer(otherEngineer)))
                .andExpect(status().isNotFound());
        // Administrators can see every request but are not the approver.
        mockMvc.perform(post("/api/requests/{id}/approve", id).with(testUsers.bearer(admin)))
                .andExpect(status().isForbidden());
        // The approver may view but not act as the requester.
        mockMvc.perform(post("/api/requests/{id}/cancel", id).with(testUsers.bearer(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotApproveTheirOwnRequest() throws Exception {
        User director = testUsers.create(Role.MANAGER);
        User lead = testUsers.createReportingTo(director, Role.MANAGER, Role.ENGINEER);
        ResourceRequest request = fixtures.submitted(lead, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());

        mockMvc.perform(post("/api/requests/{id}/approve", request.getId()).with(testUsers.bearer(lead)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
    }

    @Test
    void clientCannotChooseRequesterApproverOrStatus() throws Exception {
        String body = "{\"resourceType\": \"K8S_NAMESPACE\", \"environment\": \"PRODUCTION\", \"spec\": "
                + RequestFixtures.namespaceSpec() + ", \"justification\": \"Trying to escalate privileges\","
                + "\"requesterId\": \"" + admin.getId() + "\", \"assignedApproverId\": \"" + engineer.getId() + "\","
                + "\"status\": \"APPROVED\"}";

        create(engineer, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requesterId").value(engineer.getId().toString()))
                .andExpect(jsonPath("$.assignedApproverId").doesNotExist())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void listScopesAreEnforced() throws Exception {
        String id = submittedNamespace();
        User auditor = testUsers.create(Role.AUDITOR);

        mockMvc.perform(get("/api/requests").param("scope", "ALL").with(testUsers.bearer(engineer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests").param("scope", "ASSIGNED").with(testUsers.bearer(engineer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/requests").param("scope", "ALL").param("size", "100").with(testUsers.bearer(auditor)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/requests").with(testUsers.bearer(testUsers.createReportingTo(manager, Role.ENGINEER))))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem(id))));
        mockMvc.perform(get("/api/requests/{id}/audit", id).with(testUsers.bearer(auditor)))
                .andExpect(status().isOk());
    }

    @Test
    void auditorsAndAdminsCannotCreateRequests() throws Exception {
        create(testUsers.create(Role.AUDITOR), namespaceBody()).andExpect(status().isForbidden());
    }

    @Test
    void disabledUserCannotChangeAnythingWithAStillValidToken() throws Exception {
        var token = testUsers.bearer(engineer);
        User reloaded = users.findById(engineer.getId()).orElseThrow();
        reloaded.disable();
        users.save(reloaded);

        mockMvc.perform(post("/api/requests").with(token).contentType(MediaType.APPLICATION_JSON).content(namespaceBody()))
                .andExpect(status().isForbidden());
    }

    // --- business rules -----------------------------------------------------------------------

    @Test
    void approverIsFixedAtSubmission() throws Exception {
        String id = submittedNamespace();
        User newManager = testUsers.create(Role.MANAGER);
        mockMvc.perform(put("/api/admin/users/{id}/manager", engineer.getId()).with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"managerId\": \"" + newManager.getId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/requests/{id}/approve", id).with(testUsers.bearer(newManager)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/requests/{id}/approve", id).with(testUsers.bearer(manager)))
                .andExpect(status().isOk());
    }

    @Test
    void submitNeedsAManagerAValidSpecAndAnAllowedEnvironment() throws Exception {
        User orphan = testUsers.create(Role.ENGINEER);
        String orphanDraft = id(create(orphan, namespaceBody()));
        action(orphan, orphanDraft, "submit").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUESTER_HAS_NO_MANAGER"));

        String badSpec = id(create(engineer, "{\"resourceType\": \"K8S_NAMESPACE\", \"environment\": \"PRODUCTION\","
                + "\"spec\": {\"namespace\": \"kube-system\", \"cpuCores\": 100, \"memoryGi\": 1},"
                + "\"justification\": \"Needs a lot of CPU\"}"));
        action(engineer, badSpec, "submit").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_SPEC"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("spec.namespace")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("spec.cpuCores")));

        String deniedEnvironment = id(create(engineer, "{\"resourceType\": \"AWS_ACCOUNT\", \"environment\": \"DEVELOPMENT\","
                + "\"spec\": {}, \"justification\": \"An account for experiments\"}"));
        action(engineer, deniedEnvironment, "submit").andExpect(status().isUnprocessableContent());
    }

    @Test
    void quotaLimitsActiveRequestsPerUser() throws Exception {
        String gcpBody = "{\"resourceType\": \"GCP_PROJECT\", \"environment\": \"DEVELOPMENT\","
                + "\"spec\": {\"projectId\": \"acme-q-%s\", \"folderId\": \"1001\"}, \"justification\": \"Quota testing project\"}";
        action(engineer, id(create(engineer, gcpBody.formatted("aaa1"))), "submit").andExpect(status().isOk());

        action(engineer, id(create(engineer, gcpBody.formatted("bbb2"))), "submit")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }

    @Test
    void cancellationOnlyWhilePending() throws Exception {
        String pending = submittedNamespace();
        action(engineer, pending, "cancel").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        action(engineer, pending, "cancel").andExpect(status().isUnprocessableContent());

        String draft = id(create(engineer, namespaceBody()));
        action(engineer, draft, "cancel").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void concurrentApproveAndCancelCannotBothSucceed() throws Exception {
        for (int round = 0; round < 3; round++) {
            UUID id = UUID.fromString(submittedNamespace());
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Callable<Boolean>> tasks = List.of(
                        () -> attempt(start, () -> approvalService.approve(testUsers.principal(manager), id, null)),
                        () -> attempt(start, () -> requestService.cancel(testUsers.principal(engineer), id)));
                List<Future<Boolean>> results = new ArrayList<>();
                for (Callable<Boolean> task : tasks) {
                    results.add(pool.submit(task));
                }
                start.countDown();
                int successes = 0;
                for (Future<Boolean> result : results) {
                    successes += result.get() ? 1 : 0;
                }
                assertThat(successes).isEqualTo(1);
                long jobs = jdbc.queryForObject("SELECT count(*) FROM provisioning_job WHERE request_id = ?", Long.class, id);
                String status = jdbc.queryForObject("SELECT status FROM resource_request WHERE id = ?", String.class, id);
                assertThat(jobs).isEqualTo("APPROVED".equals(status) ? 1 : 0);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void auditTrailIsAppendOnlyInTheDatabase() throws Exception {
        String id = submittedNamespace();

        // The trigger raises SQLSTATE 42501 (insufficient_privilege); Spring wraps it, so check the root cause.
        assertThat(catchThrowable(() ->
                jdbc.update("UPDATE audit_event SET action = 'REQUEST_APPROVED' WHERE entity_id = ?::uuid", id)))
                .rootCause().hasMessageContaining("append-only");
        assertThat(catchThrowable(() ->
                jdbc.update("DELETE FROM audit_event WHERE entity_id = ?::uuid", id)))
                .rootCause().hasMessageContaining("append-only");
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static boolean attempt(CountDownLatch start, Runnable action) throws InterruptedException {
        start.await();
        try {
            action.run();
            return true;
        } catch (CloudForgeException | ConcurrencyFailureException e) {
            return false;
        }
    }

    private String submittedNamespace() {
        return fixtures.submitted(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec()).getId().toString();
    }

    private static String namespaceBody() {
        return "{\"resourceType\": \"K8S_NAMESPACE\", \"environment\": \"PRODUCTION\", \"spec\": "
                + RequestFixtures.namespaceSpec() + ", \"justification\": \"Namespace for the payments team\"}";
    }

    private ResultActions create(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/requests").with(testUsers.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions action(User user, String id, String action) throws Exception {
        return mockMvc.perform(post("/api/requests/{id}/" + action, id).with(testUsers.bearer(user)));
    }

    private static String id(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
