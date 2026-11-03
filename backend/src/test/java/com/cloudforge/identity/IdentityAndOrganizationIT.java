package com.cloudforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.identity.application.UserAdministrationService;
import com.cloudforge.identity.domain.OpaqueToken;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.support.TestUsers;
import com.cloudforge.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Identity and organization against real PostgreSQL: migrations, constraints, the recursive
 * hierarchy query, the advisory lock and the admin API end to end. Each test uses unique names, so
 * tests stay independent without truncating tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class IdentityAndOrganizationIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserAdministrationService userAdministration;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestUsers testUsers;

    User admin;

    @BeforeEach
    void createAdmin() {
        admin = testUsers.create(Role.PLATFORM_ADMIN);
    }

    @Test
    void departmentNamesAreUniqueIgnoringCase() throws Exception {
        String name = "Platform " + UUID.randomUUID();
        createDepartment(name);

        mockMvc.perform(post("/api/admin/departments").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name.toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NAME_TAKEN"));
    }

    @Test
    void teamBelongsToExistingDepartment() throws Exception {
        String departmentId = createDepartment("Dept " + UUID.randomUUID());

        mockMvc.perform(post("/api/admin/teams").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"" + departmentId + "\",\"name\":\"SRE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentId").value(departmentId))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(post("/api/admin/teams").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"" + UUID.randomUUID() + "\",\"name\":\"SRE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void assignsManagerAndRejectsCycles() throws Exception {
        User director = testUsers.create(Role.MANAGER);
        User lead = testUsers.create(Role.MANAGER);
        User engineer = testUsers.create(Role.ENGINEER);

        assignManager(lead.getId(), director.getId()).andExpect(status().isOk());
        assignManager(engineer.getId(), lead.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(lead.getId().toString()));

        // director -> lead -> director would be a cycle, two levels up.
        assignManager(director.getId(), lead.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MANAGER_CYCLE"));
    }

    @Test
    void engineerCannotBeAssignedAsManager() throws Exception {
        User engineer = testUsers.create(Role.ENGINEER);
        User otherEngineer = testUsers.create(Role.ENGINEER);

        assignManager(engineer.getId(), otherEngineer.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MANAGER_NOT_ELIGIBLE"));
    }

    @Test
    void concurrentOppositeAssignmentsCannotBothSucceed() throws Exception {
        User a = testUsers.create(Role.MANAGER);
        User b = testUsers.create(Role.MANAGER);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> tryAssign(start, a.getId(), b.getId()),
                    () -> tryAssign(start, b.getId(), a.getId()));
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
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cannotDisableManagerWithActiveReports() throws Exception {
        User manager = testUsers.create(Role.MANAGER);
        User engineer = testUsers.create(Role.ENGINEER);
        assignManager(engineer.getId(), manager.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/{id}/disable", manager.getId()).with(testUsers.bearer(admin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MANAGER_HAS_REPORTS"));
    }

    @Test
    void invitationReturnsTokenOnceAndStoresOnlyItsHash() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email.toUpperCase() + "\",\"roles\":[\"ENGINEER\"]}";

        String response = mockMvc.perform(post("/api/admin/invitations").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.invitation.email").value(email))
                .andExpect(jsonPath("$.invitation.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");
        String id = JsonPath.read(response, "$.invitation.id");

        String storedHash = jdbc.queryForObject("SELECT token_hash FROM invitation WHERE id = ?::uuid",
                String.class, id);
        assertThat(storedHash).isEqualTo(new OpaqueToken(token).hash()).doesNotContain(token);

        mockMvc.perform(get("/api/admin/invitations/{id}", id).with(testUsers.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post("/api/admin/invitations").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));
    }

    @Test
    void databaseRejectsUnnormalizedEmailsAndSelfManagement() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (id, email, password_hash, display_name, status, created_at, updated_at, version)
                VALUES (?, 'Mixed@Example.com', 'h', 'x', 'ACTIVE', now(), now(), 0)""", id))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (id, email, password_hash, display_name, status, manager_id, created_at, updated_at, version)
                VALUES (?, ?, 'h', 'x', 'ACTIVE', ?, now(), now(), 0)""", id, "self-" + id + "@example.com", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void engineerCannotReachAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/departments").with(testUsers.bearer(testUsers.create(Role.ENGINEER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitationRecordsTheIssuingAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/invitations").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"by-" + UUID.randomUUID() + "@example.com\",\"roles\":[\"ENGINEER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.invitedBy").value(admin.getId().toString()));
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/disable", admin.getId()).with(testUsers.bearer(admin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }

    private boolean tryAssign(CountDownLatch start, UUID userId, UUID managerId) throws InterruptedException {
        start.await();
        try {
            userAdministration.assignManager(admin.getId(), userId, managerId, null);
            return true;
        } catch (BusinessRuleViolationException e) {
            assertThat(e.code()).isEqualTo("MANAGER_CYCLE");
            return false;
        }
    }

    private ResultActions assignManager(UUID userId, UUID managerId)
            throws Exception {
        return mockMvc.perform(put("/api/admin/users/{id}/manager", userId).with(testUsers.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerId\":\"" + managerId + "\"}"));
    }

    private String createDepartment(String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/departments").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

}
