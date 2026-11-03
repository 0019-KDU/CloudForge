package com.cloudforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.cloudforge.common.maintenance.DataRetentionJob;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Per-client login rate limiting, lost-update protection (ETag / If-Match) and data retention. */
@SpringBootTest(properties = "cloudforge.security.login-throttle.max-attempts=3")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class SecurityHardeningIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestUsers testUsers;
    @Autowired
    DataRetentionJob retention;
    @Autowired
    JdbcTemplate jdbc;

    User admin;

    @BeforeEach
    void setUp() {
        admin = testUsers.create(Role.PLATFORM_ADMIN);
    }

    // --- login rate limit -----------------------------------------------------------------------

    @Test
    void loginIsRateLimitedPerClientAddress() throws Exception {
        String attacker = uniqueAddress();
        for (int i = 0; i < 3; i++) {
            login(attacker, "victim-" + i + "@example.com").andExpect(status().isUnauthorized());
        }

        login(attacker, "victim-4@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        // Even correct credentials are refused from that client until the window ends.
        User user = testUsers.create(Role.ENGINEER);
        login(attacker, user.getEmail(), TestUsers.PASSWORD).andExpect(status().isTooManyRequests());

        // Other clients are unaffected.
        login(uniqueAddress(), user.getEmail(), TestUsers.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void registrationIsRateLimitedToo() throws Exception {
        String client = uniqueAddress();
        for (int i = 0; i < 3; i++) {
            register(client).andExpect(status().isUnprocessableContent());
        }
        register(client).andExpect(status().isTooManyRequests());
    }

    // --- ETag / If-Match ---------------------------------------------------------------------------

    @Test
    void concurrentAdminEditsAreDetectedWithIfMatch() throws Exception {
        User manager = testUsers.create(Role.MANAGER);
        User otherManager = testUsers.create(Role.MANAGER);
        User engineer = testUsers.create(Role.ENGINEER);

        String etag = mockMvc.perform(get("/api/admin/users/{id}", engineer.getId()).with(testUsers.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getHeader("ETag");

        // Admin A saves first, using the version they read.
        assignManager(engineer, manager, etag)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));

        // Admin B still holds version 0: the change is refused instead of silently overwriting A's.
        assignManager(engineer, otherManager, etag)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
        mockMvc.perform(get("/api/admin/users/{id}", engineer.getId()).with(testUsers.bearer(admin)))
                .andExpect(jsonPath("$.managerId").value(manager.getId().toString()));

        // Clients that send no precondition keep the previous behaviour.
        assignManager(engineer, otherManager, null).andExpect(status().isOk());
        // Weak and malformed validators.
        assignManager(engineer, manager, "W/\"2\"").andExpect(status().isOk());
        assignManager(engineer, manager, "\"not-a-version\"").andExpect(status().isPreconditionFailed());
    }

    @Test
    void departmentAndPolicyEditsHonourIfMatch() throws Exception {
        String body = mockMvc.perform(post("/api/admin/departments").with(testUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Dept " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/admin/departments/{id}/name", id).with(testUsers.bearer(admin))
                        .header("If-Match", "\"7\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isPreconditionFailed());

        String policy = "{\"allowed\": false, \"constraints\": {}}";
        String version = mockMvc.perform(put("/api/admin/resource-types/GCP_PROJECT/policies/PRODUCTION")
                        .with(testUsers.bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(policy))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        // A real change bumps the version (an identical PUT changes nothing and keeps the ETag, as HTTP intends).
        String changed = "{\"allowed\": false, \"maxActivePerUser\": " + (int) (Math.random() * 1000 + 1)
                + ", \"constraints\": {}}";
        mockMvc.perform(put("/api/admin/resource-types/GCP_PROJECT/policies/PRODUCTION").with(testUsers.bearer(admin))
                        .header("If-Match", version).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", not(version)));
        mockMvc.perform(put("/api/admin/resource-types/GCP_PROJECT/policies/PRODUCTION").with(testUsers.bearer(admin))
                        .header("If-Match", version).contentType(MediaType.APPLICATION_JSON).content(policy))
                .andExpect(status().isPreconditionFailed());
    }

    // --- data retention ------------------------------------------------------------------------------

    @Test
    void retentionRemovesOnlyStaleOperationalData() {
        User user = testUsers.create(Role.ENGINEER);
        UUID oldToken = insertRefreshToken(user, "now() - interval '60 days'");
        UUID liveToken = insertRefreshToken(user, "now() + interval '1 day'");
        UUID oldRead = insertNotification(user, "now() - interval '120 days'");
        UUID recentRead = insertNotification(user, "now() - interval '1 day'");
        UUID unread = insertNotification(user, null);
        String staleClient = "login:stale-" + UUID.randomUUID();
        jdbc.update("INSERT INTO login_throttle (client_key, window_start, attempts) VALUES (?, now() - interval '2 days', 5)",
                staleClient);

        DataRetentionJob.Result result = retention.run();

        assertThat(result.refreshTokens()).isPositive();
        assertThat(exists("refresh_token", oldToken)).isFalse();
        assertThat(exists("refresh_token", liveToken)).isTrue();
        assertThat(exists("notification", oldRead)).isFalse();
        assertThat(exists("notification", recentRead)).isTrue();
        assertThat(exists("notification", unread)).as("unread notifications are never removed").isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_throttle WHERE client_key = ?", Long.class, staleClient))
                .isZero();
    }

    // --- helpers ---------------------------------------------------------------------------------------

    private static String uniqueAddress() {
        UUID random = UUID.randomUUID();
        return "198.51." + Math.floorMod(random.getMostSignificantBits(), 256) + "."
                + Math.floorMod(random.getLeastSignificantBits(), 256);
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private ResultActions login(String address, String email) throws Exception {
        return login(address, email, "not the right password");
    }

    private ResultActions login(String address, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(from(address)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions register(String address) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(from(address)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationToken\": \"guess-" + UUID.randomUUID() + "\", \"displayName\": \"X\","
                        + "\"password\": \"a long unique passphrase\"}"));
    }

    private ResultActions assignManager(User user, User manager, String ifMatch) throws Exception {
        var request = put("/api/admin/users/{id}/manager", user.getId()).with(testUsers.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"managerId\": \"" + manager.getId() + "\"}");
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mockMvc.perform(request);
    }

    private UUID insertRefreshToken(User user, String expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO refresh_token (id, user_id, family_id, token_hash, expires_at, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, " + expiresAt + ", now(), now(), 0)", id, user.getId(), UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""));
        return id;
    }

    private UUID insertNotification(User user, String readAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notification (id, recipient_id, type, title, body, read_at, created_at) "
                + "VALUES (?, ?, 'REQUEST_APPROVED', 't', 'b', " + (readAt == null ? "NULL" : readAt) + ", now())",
                id, user.getId());
        return id;
    }

    private boolean exists(String table, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Long.class, id) > 0;
    }
}
