package com.cloudforge.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;
import com.cloudforge.support.RequestFixtures;
import com.cloudforge.support.TestUsers;
import com.cloudforge.support.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The real wiring end to end, as in the {@code local} profile: scheduler on, Kubernetes in
 * {@code mock} mode, no test doubles. An approved request must reach PROVISIONED by itself, through
 * the simulated long-running operation (start → poll).
 *
 * <p>{@code @DirtiesContext} stops this context's scheduler afterwards so it cannot pick up jobs that
 * other test classes create in the shared database.
 */
@SpringBootTest(properties = {
        "cloudforge.provisioning.worker.enabled=true",
        "cloudforge.provisioning.worker.poll-interval=PT0.5S",
        "cloudforge.provisioning.kubernetes.mode=mock"})
@Import({TestcontainersConfiguration.class, TestUsers.class, RequestFixtures.class})
@DirtiesContext
class ScheduledMockProvisioningIT {

    @Autowired
    TestUsers testUsers;
    @Autowired
    RequestFixtures fixtures;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void approvedRequestIsProvisionedBySchedulerWithMockProvider() throws Exception {
        User admin = testUsers.create(Role.PLATFORM_ADMIN);
        User manager = testUsers.create(Role.MANAGER);
        User engineer = testUsers.createReportingTo(manager, Role.ENGINEER);
        fixtures.allow(admin, ResourceTypeCode.K8S_NAMESPACE, Environment.DEVELOPMENT, RequestFixtures.NAMESPACE_LIMITS, null);

        UUID id = fixtures.approved(engineer, manager, ResourceTypeCode.K8S_NAMESPACE, Environment.DEVELOPMENT,
                RequestFixtures.namespaceSpec());

        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        String status;
        do {
            Thread.sleep(250);
            status = jdbc.queryForObject("SELECT status FROM resource_request WHERE id = ?", String.class, id);
        } while (!"PROVISIONED".equals(status) && Instant.now().isBefore(deadline));

        assertThat(status).isEqualTo("PROVISIONED");
        assertThat(jdbc.queryForObject("SELECT external_resource_id FROM provisioning_result WHERE request_id = ?",
                String.class, id)).isEqualTo("mock-k8s_namespace-" + id);
        assertThat(jdbc.queryForList("SELECT kind FROM provisioning_attempt a JOIN provisioning_job j ON j.id = a.job_id "
                + "WHERE j.request_id = ? ORDER BY a.started_at", String.class, id)).containsExactly("START", "POLL");
    }
}
