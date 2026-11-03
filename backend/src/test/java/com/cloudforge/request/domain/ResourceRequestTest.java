package com.cloudforge.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.junit.jupiter.api.Test;

class ResourceRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private final UUID requester = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();

    private ResourceRequest draft() {
        return new ResourceRequest("CF-2026-000001", requester, ResourceTypeCode.K8S_NAMESPACE, Environment.DEVELOPMENT,
                "{}", "Needed for the payments service");
    }

    @Test
    void submitCapturesApproverAndTime() {
        ResourceRequest request = draft();

        request.submit(manager, NOW);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        assertThat(request.getAssignedApproverId()).isEqualTo(manager);
        assertThat(request.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    void requesterCannotBeTheApprover() {
        assertThatThrownBy(() -> draft().submit(requester, NOW))
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_FORBIDDEN");
    }

    @Test
    void submittedRequestIsFrozen() {
        ResourceRequest request = draft();
        request.submit(manager, NOW);

        assertThatThrownBy(() -> request.updateDraft(Environment.PRODUCTION, "{}", "Changed after submission"))
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_EDITABLE");
    }

    @Test
    void rejectedRequestCanNeverBeProvisioned() {
        ResourceRequest request = draft();
        request.submit(manager, NOW);
        request.reject(NOW);

        assertThatThrownBy(request::startProvisioning).hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
        assertThatThrownBy(() -> request.approve(NOW)).hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void draftCannotSkipApproval() {
        ResourceRequest request = draft();

        assertThatThrownBy(() -> request.approve(NOW)).hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
        assertThatThrownBy(request::startProvisioning).hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
        assertThatThrownBy(request::cancel).hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void failedProvisioningCanBeRetriedAndKeepsDecisionTime() {
        ResourceRequest request = draft();
        request.submit(manager, NOW);
        request.approve(NOW);
        request.startProvisioning();
        request.markProvisioningFailed();

        request.startProvisioning();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PROVISIONING);
        assertThat(request.getDecidedAt()).isEqualTo(NOW);
        assertThat(request.getAssignedApproverId()).isEqualTo(manager);
    }
}
