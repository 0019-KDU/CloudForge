package com.cloudforge.provisioning.api;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.cloudforge.common.api.PageResponse;
import com.cloudforge.provisioning.domain.JobStatus;
import com.cloudforge.provisioning.repository.ProvisioningJobRepository;
import com.cloudforge.provisioning.repository.ProvisioningJobSummary;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operations view of the provisioning queue, for example "everything FAILED" or "what is waiting on
 * a provider". Platform administrators and auditors only; per-request details stay at
 * {@code /api/requests/{id}/provisioning}. Read-only, so it goes straight to the repository.
 */
@RestController
@RequestMapping("/api/provisioning-jobs")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'AUDITOR')")
class ProvisioningJobsController {

    private final ProvisioningJobRepository jobs;

    ProvisioningJobsController(ProvisioningJobRepository jobs) {
        this.jobs = jobs;
    }

    record SummaryResponse(long total, Map<JobStatus, Long> byStatus) {
    }

    /** Dashboard counters: jobs per status (every status present, zero if none). */
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    SummaryResponse summary() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            counts.put(status, 0L);
        }
        jobs.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getCount()));
        return new SummaryResponse(counts.values().stream().mapToLong(Long::longValue).sum(), counts);
    }

    /** {@code ?status=FAILED&status=AWAITING_PROVIDER&resourceType=AWS_ACCOUNT}; newest change first. */
    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<ProvisioningJobSummary> list(@RequestParam(required = false) Set<JobStatus> status,
            @RequestParam(required = false) ResourceTypeCode resourceType, Pageable pageable) {
        Set<JobStatus> statuses = status == null || status.isEmpty() ? EnumSet.allOf(JobStatus.class) : status;
        return PageResponse.of(jobs.summaries(statuses, resourceType,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())), summary -> summary);
    }
}
