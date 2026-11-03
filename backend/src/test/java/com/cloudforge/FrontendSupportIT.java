package com.cloudforge;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.servlet.http.Cookie;

import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Endpoints added for the frontend: user directory, policy limits in the catalog, request search,
 * draft deletion, the provisioning job list, profile and password self-service, and password reset.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class, RequestFixtures.class})
class FrontendSupportIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestUsers testUsers;
    @Autowired
    RequestFixtures fixtures;

    User admin;
    User manager;
    User engineer;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.PLATFORM_ADMIN);
        manager = testUsers.create(Role.MANAGER);
        engineer = testUsers.createReportingTo(manager, Role.ENGINEER);
        fixtures.allow(admin, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION, RequestFixtures.NAMESPACE_LIMITS, null);
    }

    // --- G1: directory ------------------------------------------------------------------------------

    @Test
    void anyUserCanResolveNamesButNothingSensitive() throws Exception {
        mockMvc.perform(get("/api/users/directory").param("ids", engineer.getId() + "," + manager.getId() + "," + UUID.randomUUID())
                        .with(testUsers.bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].displayName").value(containsInAnyOrder(engineer.getDisplayName(), manager.getDisplayName())))
                .andExpect(jsonPath("$[0].roles").doesNotExist())
                .andExpect(jsonPath("$[0].managerId").doesNotExist());

        String tooMany = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID().toString()).collect(Collectors.joining(","));
        mockMvc.perform(get("/api/users/directory").param("ids", tooMany).with(testUsers.bearer(engineer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_IDS"));
        mockMvc.perform(get("/api/users/directory").param("ids", engineer.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void directoryIncludesTeamAndDepartment() throws Exception {
        String departmentName = "Payments " + UUID.randomUUID();
        String department = JsonPath.read(mockMvc.perform(post("/api/admin/departments").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + departmentName + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String team = JsonPath.read(mockMvc.perform(post("/api/admin/teams").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\": \"" + department + "\", \"name\": \"Ledger\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(put("/api/admin/users/{id}/team", engineer.getId()).with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teamId\": \"" + team + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/directory").param("ids", engineer.getId() + "," + manager.getId())
                        .with(testUsers.bearer(manager)))
                .andExpect(jsonPath("$[?(@.id == '" + engineer.getId() + "')].teamName").value(hasItem("Ledger")))
                .andExpect(jsonPath("$[?(@.id == '" + engineer.getId() + "')].departmentName").value(hasItem(departmentName)))
                .andExpect(jsonPath("$[?(@.id == '" + manager.getId() + "')].teamName").value(hasItem(nullValue())));
    }

    // --- G9: dashboard counters ------------------------------------------------------------------------

    @Test
    void summaryCountsRequestsPerStatusWithinTheCallersScope() throws Exception {
        fixtures.draft(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION, RequestFixtures.namespaceSpec());
        fixtures.submitted(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION, RequestFixtures.namespaceSpec());
        fixtures.submitted(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION, RequestFixtures.namespaceSpec());

        mockMvc.perform(get("/api/requests/summary").with(testUsers.bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("MINE"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byStatus.DRAFT").value(1))
                .andExpect(jsonPath("$.byStatus.PENDING_APPROVAL").value(2))
                .andExpect(jsonPath("$.byStatus.PROVISIONED").value(0));
        mockMvc.perform(get("/api/requests/summary").param("scope", "ASSIGNED").with(testUsers.bearer(manager)))
                .andExpect(jsonPath("$.byStatus.PENDING_APPROVAL").value(2))
                .andExpect(jsonPath("$.byStatus.DRAFT").value(0));
        mockMvc.perform(get("/api/requests/summary").param("scope", "ALL").with(testUsers.bearer(engineer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/provisioning-jobs/summary").with(testUsers.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.QUEUED").isNumber())
                .andExpect(jsonPath("$.total").isNumber());
        mockMvc.perform(get("/api/provisioning-jobs/summary").with(testUsers.bearer(engineer)))
                .andExpect(status().isForbidden());
    }

    // --- G3: policy limits in the catalog ----------------------------------------------------------

    @Test
    void engineersSeeTheLimitsOfAllowedEnvironments() throws Exception {
        mockMvc.perform(get("/api/resource-types/K8S_NAMESPACE").with(testUsers.bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments[?(@.environment == 'PRODUCTION')].constraints.maxCpuCores").value(hasItem(8)))
                .andExpect(jsonPath("$.environments[?(@.environment == 'PRODUCTION')].constraints.maxMemoryGi").value(hasItem(32)))
                .andExpect(jsonPath("$.environments[*].constraints").value(everyItem(notNullValue())));
    }

    // --- G4, G5: search and multi-status filter, draft deletion ----------------------------------------

    @Test
    void requestsCanBeSearchedAndFilteredByMultipleStatuses() throws Exception {
        ResourceRequest draft = fixtures.draft(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());
        ResourceRequest pending = fixtures.submitted(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());

        mockMvc.perform(get("/api/requests").param("q", pending.getRequestNumber().toLowerCase()).with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(pending.getId().toString())));
        mockMvc.perform(get("/api/requests").param("q", "integration TEST").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/requests").param("q", "100%_").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/requests").param("status", "DRAFT").param("status", "PENDING_APPROVAL")
                        .with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/requests").param("status", "APPROVED").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/requests").param("status", "DRAFT").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.content[0].id").value(draft.getId().toString()));
    }

    @Test
    void onlyOwnDraftsCanBeDeleted() throws Exception {
        ResourceRequest draft = fixtures.draft(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());
        ResourceRequest pending = fixtures.submitted(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());

        mockMvc.perform(delete("/api/requests/{id}", draft.getId()).with(testUsers.bearer(manager)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/requests/{id}", pending.getId()).with(testUsers.bearer(engineer)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_DELETABLE"));

        mockMvc.perform(delete("/api/requests/{id}", draft.getId()).with(testUsers.bearer(engineer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/requests/{id}", draft.getId()).with(testUsers.bearer(engineer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audit-events").param("entityId", draft.getId().toString()).with(testUsers.bearer(admin)))
                .andExpect(jsonPath("$.content[*].action").value(hasItem("REQUEST_DELETED")));
    }

    // --- G6: provisioning job list ---------------------------------------------------------------------

    @Test
    void provisioningJobsAreListedForOperators() throws Exception {
        UUID requestId = fixtures.approved(engineer, manager, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());

        mockMvc.perform(get("/api/provisioning-jobs").with(testUsers.bearer(engineer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/provisioning-jobs").param("status", "QUEUED").param("resourceType", "K8S_NAMESPACE")
                        .param("size", "100").with(testUsers.bearer(testUsers.create(Role.AUDITOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].requestId").value(hasItem(requestId.toString())))
                .andExpect(jsonPath("$.content[*].status").value(everyItem(is("QUEUED"))))
                .andExpect(jsonPath("$.content[0].requestNumber").isNotEmpty());
    }

    // --- profile and password self-service ---------------------------------------------------------------

    @Test
    void usersCanRenameThemselves() throws Exception {
        mockMvc.perform(put("/api/users/me").with(testUsers.bearer(engineer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Ada Lovelace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));
    }

    @Test
    void passwordChangeNeedsTheCurrentPasswordAndSignsOutEverywhere() throws Exception {
        String oldCookie = login(engineer.getEmail(), TestUsers.PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("cf_refresh").getValue();

        changePassword("wrong current password", "a brand new passphrase").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        changePassword(TestUsers.PASSWORD, "short").andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        changePassword(TestUsers.PASSWORD, "a brand new passphrase").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("cf_refresh", oldCookie)))
                .andExpect(status().isUnauthorized());
        login(engineer.getEmail(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());
        login(engineer.getEmail(), "a brand new passphrase").andExpect(status().isOk());
    }

    @Test
    void forgottenPasswordIsResetWithAnAdminIssuedToken() throws Exception {
        for (int i = 0; i < 5; i++) { // lock the account by "forgetting" the password
            login(engineer.getEmail(), "forgotten password " + i);
        }

        mockMvc.perform(post("/api/admin/users/{id}/password-reset", engineer.getId()).with(testUsers.bearer(manager)))
                .andExpect(status().isForbidden());
        String first = issueReset();
        String token = issueReset(); // a newer token invalidates the first one

        reset(first, "my recovered passphrase").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        reset(token, "my recovered passphrase").andExpect(status().isNoContent());
        reset(token, "another passphrase here").andExpect(status().isUnprocessableContent());

        login(engineer.getEmail(), "my recovered passphrase").andExpect(status().isOk());
        mockMvc.perform(get("/api/audit-events").param("entityId", engineer.getId().toString()).with(testUsers.bearer(admin)))
                .andExpect(jsonPath("$.content[*].action").value(hasItem("PASSWORD_RESET_COMPLETED")))
                .andExpect(jsonPath("$.content[*].details").value(not(hasItem(hasKey("token")))));
    }

    // --- helpers -----------------------------------------------------------------------------------------

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions changePassword(String current, String next) throws Exception {
        return mockMvc.perform(post("/api/users/me/password").with(testUsers.bearer(engineer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}"));
    }

    private String issueReset() throws Exception {
        String response = mockMvc.perform(post("/api/admin/users/{id}/password-reset", engineer.getId())
                        .with(testUsers.bearer(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\", \"newPassword\": \"" + password + "\"}"));
    }
}
