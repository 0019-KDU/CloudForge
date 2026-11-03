package com.cloudforge.approval.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.cloudforge.approval.application.ApprovalService;
import com.cloudforge.approval.domain.ApprovalRecord;
import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.common.security.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Decision endpoints. The approver is always the authenticated caller, never a request field. */
@RestController
@RequestMapping("/api/requests/{id}")
class ApprovalController {

    private final ApprovalService approvals;

    ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    record ApproveRequest(@Size(max = 2000) String comment) {
    }

    record RejectRequest(@NotBlank @Size(max = 2000) String comment) {
    }

    record DecisionResponse(UUID id, UUID requestId, UUID approverId, ApprovalRecord.Decision decision, String comment,
            Instant decidedAt) {

        static DecisionResponse from(ApprovalRecord record) {
            return new DecisionResponse(record.getId(), record.getRequestId(), record.getApproverId(),
                    record.getDecision(), record.getComment(), record.getDecidedAt());
        }
    }

    /** The body is optional: an approval may be given without a comment. */
    @PostMapping("/approve")
    DecisionResponse approve(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApproveRequest body) {
        return DecisionResponse.from(approvals.approve(user, id, body == null ? null : body.comment()));
    }

    @PostMapping("/reject")
    DecisionResponse reject(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody RejectRequest body) {
        return DecisionResponse.from(approvals.reject(user, id, body.comment()));
    }

    @GetMapping("/approval")
    DecisionResponse decision(CurrentUser user, @PathVariable UUID id) {
        return approvals.decisionFor(user, id).map(DecisionResponse::from)
                .orElseThrow(() -> new NotFoundException("DECISION_NOT_FOUND", "This request has not been decided yet."));
    }
}
