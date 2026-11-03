package com.cloudforge.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.provisioning.application.ProvisioningWorker;
import com.cloudforge.provisioning.provider.ProviderResolver;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Completed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Failed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.InProgress;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;
import com.cloudforge.support.RequestFixtures;
import com.cloudforge.support.TestUsers;
import com.cloudforge.support.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The provisioning job engine against real PostgreSQL with a scripted provider: retries, long-running
 * operations, permanent failures, crash recovery, concurrent workers and policy re-checks. No cloud
 * is involved. Owns the AWS_ACCOUNT/DEVELOPMENT and AWS_ACCOUNT/STAGING policies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class, RequestFixtures.class})
@ExtendWith(OutputCaptureExtension.class)
class ProvisioningEngineIT {

    private static final String OU = "ou-ab12-cd34ef56";
    private static final String CONSTRAINTS = "{\"allowedOrganizationalUnitIds\": [\"" + OU + "\"]}";

    @MockitoBean
    ProviderResolver providerResolver;

    @Autowired
    ProvisioningWorker worker;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestUsers testUsers;
    @Autowired
    RequestFixtures fixtures;
    @Autowired
    JdbcTemplate jdbc;

    final ScriptedProvider provider = new ScriptedProvider();
    User admin;
    User manager;
    User engineer;

    @BeforeEach
    void setUp() {
        // Jobs left behind by other test classes must not be picked up by this worker.
        jdbc.update("UPDATE provisioning_job SET status = 'FAILED', locked_by = NULL, locked_until = NULL "
                + "WHERE status NOT IN ('SUCCEEDED', 'FAILED')");
        provider.reset();
        when(providerResolver.resolve(any())).thenReturn(Optional.of(provider));
        admin = testUsers.create(Role.PLATFORM_ADMIN);
        manager = testUsers.create(Role.MANAGER);
        engineer = testUsers.createReportingTo(manager, Role.ENGINEER);
        fixtures.allow(admin, ResourceTypeCode.AWS_ACCOUNT, Environment.DEVELOPMENT, CONSTRAINTS, null);
        fixtures.allow(admin, ResourceTypeCode.AWS_ACCOUNT, Environment.STAGING, CONSTRAINTS, null);
    }

    @Test
    void successfulProvisioningStoresResultAndNotifies() throws Exception {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        provider.then(new Completed("123456789012", Map.of("accountId", "123456789012")));

        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(requestStatus(id)).isEqualTo("PROVISIONED");
        mockMvc.perform(get("/api/requests/{id}/provisioning", id).with(testUsers.bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.externalResourceId").value("123456789012"))
                .andExpect(jsonPath("$.attempts.length()").value(1));
        assertThat(auditActions(id)).containsSubsequence("REQUEST_APPROVED", "PROVISIONING_QUEUED",
                "PROVISIONING_STARTED", "PROVISIONING_SUCCEEDED");
        assertThat(notificationTypes(engineer)).contains("REQUEST_APPROVED", "PROVISIONING_SUCCEEDED");
        assertThat(provider.calls.getFirst().command().idempotencyKey()).isEqualTo("cloudforge-" + id);
    }

    @Test
    void longRunningOperationIsPolledUntilDone() {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        provider.then(new InProgress("car-123", Duration.ofSeconds(30)))
                .then(new InProgress("car-123", Duration.ofSeconds(30)))
                .then(new Completed("210987654321", Map.of()));

        worker.runOnce();
        assertThat(jobStatus(id)).isEqualTo("AWAITING_PROVIDER");
        assertThat(requestStatus(id)).isEqualTo("PROVISIONING");
        assertThat(worker.runOnce()).as("not due before pollAfter").isZero();

        makeDue(id);
        worker.runOnce();
        makeDue(id);
        worker.runOnce();

        assertThat(requestStatus(id)).isEqualTo("PROVISIONED");
        assertThat(provider.calls).extracting(ScriptedProvider.Call::kind).containsExactly("START", "POLL", "POLL");
        assertThat(provider.calls.get(1).operationId()).isEqualTo("car-123");
        assertThat(attemptCount(id)).as("polls do not consume retry attempts").isEqualTo(1);
    }

    @Test
    void transientFailuresAreRetriedWithBackoff(CapturedOutput output) {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        provider.then(Failed.retryable("PROVIDER_THROTTLED", "Too many requests"))
                .thenThrow(new RuntimeException("connection reset; secret=hunter2"))
                .then(new Completed("111122223333", Map.of()));

        worker.runOnce();
        assertThat(jobStatus(id)).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT next_run_at FROM provisioning_job WHERE request_id = ?",
                Timestamp.class, id).toInstant()).as("backed off").isAfter(Instant.now().plusSeconds(10));
        assertThat(worker.runOnce()).isZero();

        makeDue(id);
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM provisioning_job WHERE request_id = ?",
                String.class, id)).isEqualTo("PROVIDER_ERROR");
        assertThat(jdbc.queryForObject("SELECT last_error_message FROM provisioning_job WHERE request_id = ?",
                String.class, id)).doesNotContain("hunter2");

        makeDue(id);
        worker.runOnce();
        assertThat(requestStatus(id)).isEqualTo("PROVISIONED");
        assertThat(attemptCount(id)).isEqualTo(3);
        assertThat(auditActions(id)).filteredOn("PROVISIONING_ATTEMPT_FAILED"::equals).hasSize(2);
        // The unexpected exception is logged with its stack trace, but the secret in its message is masked.
        assertThat(output).contains("connection reset").doesNotContain("hunter2");
    }

    @Test
    void exhaustedRetriesFailButKeepTheApprovalHistory() {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        for (int i = 0; i < 5; i++) {
            provider.then(Failed.retryable("PROVIDER_UNAVAILABLE", "Service unavailable"));
        }

        for (int i = 0; i < 5; i++) {
            makeDue(id);
            worker.runOnce();
        }

        assertThat(jobStatus(id)).isEqualTo("FAILED");
        assertThat(requestStatus(id)).isEqualTo("PROVISIONING_FAILED");
        assertThat(jdbc.queryForObject("SELECT decision FROM approval_record WHERE request_id = ?", String.class, id))
                .isEqualTo("APPROVED");
        assertThat(notificationTypes(engineer)).contains("PROVISIONING_FAILED");
    }

    @Test
    void permanentFailureStopsImmediatelyAndAdminCanRetry() throws Exception {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        provider.then(Failed.permanent("ACCESS_DENIED", "The provisioning role lacks organizations:CreateAccount"))
                .then(new Completed("999988887777", Map.of()));

        worker.runOnce();
        assertThat(requestStatus(id)).isEqualTo("PROVISIONING_FAILED");
        assertThat(attemptCount(id)).isEqualTo(1);

        mockMvc.perform(post("/api/requests/{id}/provisioning/retry", id).with(testUsers.bearer(engineer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/requests/{id}/provisioning/retry", id).with(testUsers.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        assertThat(requestStatus(id)).isEqualTo("PROVISIONING");

        worker.runOnce();
        assertThat(requestStatus(id)).isEqualTo("PROVISIONED");
        assertThat(provider.calls).extracting(c -> c.command().idempotencyKey()).containsOnly("cloudforge-" + id);
        mockMvc.perform(post("/api/requests/{id}/provisioning/retry", id).with(testUsers.bearer(admin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROVISIONING_NOT_FAILED"));
    }

    @Test
    void concurrentWorkersExecuteEachJobExactlyOnce() throws Exception {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        provider.latency = Duration.ofMillis(300);
        provider.then(new Completed("444455556666", Map.of())).then(new Completed("duplicate", Map.of()));

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(worker::runOnce);
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(worker::runOnce);

        assertThat(first.get() + second.get()).isEqualTo(1);
        assertThat(provider.calls).hasSize(1);
        assertThat(requestStatus(id)).isEqualTo("PROVISIONED");
    }

    @Test
    void jobsOfCrashedWorkersAreRecoveredAfterLeaseExpiry() {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        jdbc.update("UPDATE provisioning_job SET status = 'RUNNING', locked_by = 'crashed-worker', "
                + "locked_until = ? WHERE request_id = ?", past(Duration.ofMinutes(-5)), id);
        provider.then(new Completed("777766665555", Map.of()));

        assertThat(worker.runOnce()).as("lease still valid").isZero();

        jdbc.update("UPDATE provisioning_job SET locked_until = ? WHERE request_id = ?", past(Duration.ofMinutes(1)), id);
        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(requestStatus(id)).isEqualTo("PROVISIONED");
    }

    @Test
    void tightenedPolicyBlocksProvisioningWithoutCallingTheProvider() {
        UUID id = approvedAccount(Environment.STAGING);
        fixtures.allow(admin, ResourceTypeCode.AWS_ACCOUNT, Environment.STAGING,
                "{\"allowedOrganizationalUnitIds\": [\"ou-zz99-yyyyyyyy\"]}", null);
        try {
            worker.runOnce();

            assertThat(provider.calls).isEmpty();
            assertThat(requestStatus(id)).isEqualTo("PROVISIONING_FAILED");
            assertThat(jdbc.queryForObject("SELECT last_error_code FROM provisioning_job WHERE request_id = ?",
                    String.class, id)).isEqualTo("POLICY_VIOLATION");
        } finally {
            fixtures.allow(admin, ResourceTypeCode.AWS_ACCOUNT, Environment.STAGING, CONSTRAINTS, null);
        }
    }

    @Test
    void longRunningOperationTimesOut() {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        provider.then(new InProgress("op-slow", Duration.ofSeconds(5))).then(new InProgress("op-slow", Duration.ofSeconds(5)));
        worker.runOnce();

        jdbc.update("UPDATE provisioning_job SET operation_started_at = ?, next_run_at = ? WHERE request_id = ?",
                past(Duration.ofHours(3)), past(Duration.ofMinutes(1)), id);
        worker.runOnce();

        assertThat(jobStatus(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM provisioning_job WHERE request_id = ?",
                String.class, id)).isEqualTo("OPERATION_TIMEOUT");
    }

    @Test
    void disabledProviderFailsTheJobPermanently() {
        UUID id = approvedAccount(Environment.DEVELOPMENT);
        when(providerResolver.resolve(any())).thenReturn(Optional.empty());

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT last_error_code FROM provisioning_job WHERE request_id = ?",
                String.class, id)).isEqualTo("PROVIDER_DISABLED");
        assertThat(requestStatus(id)).isEqualTo("PROVISIONING_FAILED");
    }

    @Test
    void aJobForANonApprovedRequestIsNeverExecuted() {
        UUID requestId = fixtures.submitted(engineer, ResourceTypeCode.AWS_ACCOUNT, Environment.DEVELOPMENT, accountSpec())
                .getId();
        // Simulate a corrupted queue: a job for a request that was never approved.
        jdbc.update("""
                INSERT INTO provisioning_job (id, request_id, resource_type_code, status, attempt_count, next_run_at,
                    idempotency_key, created_at, updated_at, version)
                VALUES (gen_random_uuid(), ?, 'AWS_ACCOUNT', 'QUEUED', 0, ?, ?, now(), now(), 0)""",
                requestId, past(Duration.ofMinutes(1)), "rogue-" + requestId);

        worker.runOnce();

        assertThat(provider.calls).isEmpty();
        assertThat(jobStatus(requestId)).isEqualTo("FAILED");
        assertThat(requestStatus(requestId)).isEqualTo("PENDING_APPROVAL");
    }

    // --- helpers ----------------------------------------------------------------------------------

    private UUID approvedAccount(Environment environment) {
        return fixtures.approved(engineer, manager, ResourceTypeCode.AWS_ACCOUNT, environment, accountSpec());
    }

    private static String accountSpec() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return "{\"accountName\": \"acct-" + suffix + "\", \"email\": \"aws-" + suffix + "@acme.com\", "
                + "\"organizationalUnitId\": \"" + OU + "\"}";
    }

    /** A timestamp from the JVM clock, which is the clock the worker uses. */
    private static Timestamp past(Duration ago) {
        return Timestamp.from(Instant.now().minus(ago));
    }

    private void makeDue(UUID requestId) {
        jdbc.update("UPDATE provisioning_job SET next_run_at = ? WHERE request_id = ?", past(Duration.ofMinutes(1)), requestId);
    }

    private String requestStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM resource_request WHERE id = ?", String.class, id);
    }

    private String jobStatus(UUID requestId) {
        return jdbc.queryForObject("SELECT status FROM provisioning_job WHERE request_id = ?", String.class, requestId);
    }

    private int attemptCount(UUID requestId) {
        return jdbc.queryForObject("SELECT attempt_count FROM provisioning_job WHERE request_id = ?", Integer.class, requestId);
    }

    private List<String> auditActions(UUID requestId) {
        return jdbc.queryForList("SELECT action FROM audit_event WHERE entity_id = ? ORDER BY occurred_at, id",
                String.class, requestId);
    }

    private List<String> notificationTypes(User user) {
        return jdbc.queryForList("SELECT type FROM notification WHERE recipient_id = ?", String.class, user.getId());
    }
}
