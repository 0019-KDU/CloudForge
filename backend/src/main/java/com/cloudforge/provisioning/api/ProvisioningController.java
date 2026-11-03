package com.cloudforge.provisioning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.provisioning.application.ProvisioningJobService;
import com.cloudforge.provisioning.application.ProvisioningJobService.ProvisioningStatus;
import com.cloudforge.provisioning.domain.JobStatus;
import com.cloudforge.provisioning.domain.ProvisioningAttempt;
import com.cloudforge.provisioning.domain.ProvisioningJob;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/requests/{id}/provisioning")
class ProvisioningController {

    private final ProvisioningJobService provisioning;
    private final JsonMapper jsonMapper;

    ProvisioningController(ProvisioningJobService provisioning, JsonMapper jsonMapper) {
        this.provisioning = provisioning;
        this.jsonMapper = jsonMapper;
    }

    record AttemptResponse(int attemptNumber, ProvisioningAttempt.Kind kind, ProvisioningAttempt.Outcome outcome,
            Instant startedAt, Instant finishedAt, String errorCode, String errorMessage) {
    }

    record ResultResponse(String externalResourceId, JsonNode details, Instant createdAt) {
    }

    record ProvisioningResponse(JobStatus status, int attemptCount, Instant nextRunAt, String lastErrorCode,
            String lastErrorMessage, boolean awaitingProvider, ResultResponse result, List<AttemptResponse> attempts) {
    }

    record RetryResponse(JobStatus status, Instant nextRunAt) {
    }

    @GetMapping
    ProvisioningResponse status(CurrentUser user, @PathVariable UUID id) {
        ProvisioningStatus status = provisioning.status(user, id);
        ProvisioningJob job = status.job();
        // Provider operation IDs stay internal: they are handles into the provider's control plane.
        return new ProvisioningResponse(job.getStatus(), job.getAttemptCount(), job.getNextRunAt(),
                job.getLastErrorCode(), job.getLastErrorMessage(), job.getStatus() == JobStatus.AWAITING_PROVIDER,
                status.result().map(r -> new ResultResponse(r.getExternalResourceId(),
                        jsonMapper.readTree(r.getDetailsJson()), r.getCreatedAt())).orElse(null),
                status.attempts().stream().map(a -> new AttemptResponse(a.getAttemptNumber(), a.getKind(),
                        a.getOutcome(), a.getStartedAt(), a.getFinishedAt(), a.getErrorCode(), a.getErrorMessage()))
                        .toList());
    }

    @PostMapping("/retry")
    RetryResponse retry(CurrentUser user, @PathVariable UUID id) {
        ProvisioningJob job = provisioning.retry(user, id);
        return new RetryResponse(job.getStatus(), job.getNextRunAt());
    }
}
