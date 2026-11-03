package com.cloudforge.notification;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.web.servlet.MockMvc;

/** Notifications after commit, per-user isolation, and the organization-wide audit search. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class, RequestFixtures.class})
class NotificationAndAuditIT {

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

    @Test
    void approverAndRequesterAreNotifiedAndOnlyTheyCanReadTheirNotifications() throws Exception {
        UUID requestId = fixtures.approved(engineer, manager, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());

        mockMvc.perform(get("/api/notifications").with(testUsers.bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("APPROVAL_REQUESTED"))
                .andExpect(jsonPath("$.content[0].requestId").value(requestId.toString()));

        String body = mockMvc.perform(get("/api/notifications").param("unreadOnly", "true").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.content[*].type").value(hasItem("REQUEST_APPROVED")))
                .andReturn().getResponse().getContentAsString();
        String notificationId = JsonPath.read(body, "$.content[0].id");

        // Someone else's notification ID behaves exactly like a nonexistent one.
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).with(testUsers.bearer(manager)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).with(testUsers.bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mockMvc.perform(post("/api/notifications/read-all").with(testUsers.bearer(engineer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void rejectedRequestNotifiesRequesterWithTheComment() throws Exception {
        ResourceRequest request = fixtures.submitted(engineer, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());
        mockMvc.perform(post("/api/requests/{id}/reject", request.getId()).with(testUsers.bearer(manager))
                        .contentType("application/json").content("{\"comment\": \"Use the shared cluster\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.content[0].type").value("REQUEST_REJECTED"))
                .andExpect(jsonPath("$.content[0].body").value(containsString("Use the shared cluster")));
    }

    @Test
    void auditSearchIsForAuditorsAndAdminsOnly() throws Exception {
        UUID requestId = fixtures.approved(engineer, manager, ResourceTypeCode.K8S_NAMESPACE, Environment.PRODUCTION,
                RequestFixtures.namespaceSpec());
        User auditor = testUsers.create(Role.AUDITOR);

        mockMvc.perform(get("/api/audit-events").with(testUsers.bearer(engineer))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit-events").with(testUsers.bearer(manager))).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit-events").param("entityType", "RESOURCE_REQUEST")
                        .param("entityId", requestId.toString()).with(testUsers.bearer(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].entityId").value(everyItem(is(requestId.toString()))))
                .andExpect(jsonPath("$.content[*].action").value(hasItem("REQUEST_APPROVED")));

        mockMvc.perform(get("/api/audit-events").param("actorId", manager.getId().toString())
                        .param("action", "REQUEST_APPROVED").with(testUsers.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void securityEventsAreAudited() throws Exception {
        User user = testUsers.create(Role.ENGINEER);
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\": \"" + user.getEmail() + "\", \"password\": \"wrong password!\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/audit-events").param("entityType", "USER").param("entityId", user.getId().toString())
                        .with(testUsers.bearer(admin)))
                .andExpect(jsonPath("$.content[*].action").value(hasItem("LOGIN_FAILED")))
                .andExpect(jsonPath("$.content[*].actorType").value(hasItem("SYSTEM")));
    }
}
