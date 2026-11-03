package com.cloudforge.request.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.cloudforge.request.domain.RequestStatus;
import com.cloudforge.request.domain.ResourceRequest;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Request bodies deliberately have no requester, approver or status fields: those are derived on
 * the server from the authenticated user and the state machine. Unknown JSON fields are ignored.
 */
final class RequestDtos {

    private RequestDtos() {
    }

    record CreateRequest(@NotNull ResourceTypeCode resourceType, @NotNull Environment environment,
            @NotNull JsonNode spec, @NotBlank @Size(min = 10, max = 2000) String justification) {
    }

    record UpdateDraftRequest(@NotNull Environment environment, @NotNull JsonNode spec,
            @NotBlank @Size(min = 10, max = 2000) String justification) {
    }

    record RequestResponse(UUID id, String requestNumber, UUID requesterId, UUID assignedApproverId,
            ResourceTypeCode resourceType, Environment environment, JsonNode spec, String justification,
            RequestStatus status, Instant submittedAt, Instant decidedAt, Instant createdAt, Instant updatedAt) {

        static RequestResponse from(ResourceRequest request, JsonMapper jsonMapper) {
            return new RequestResponse(request.getId(), request.getRequestNumber(), request.getRequesterId(),
                    request.getAssignedApproverId(), request.getResourceType(), request.getEnvironment(),
                    jsonMapper.readTree(request.getSpecJson()), request.getJustification(), request.getStatus(),
                    request.getSubmittedAt(), request.getDecidedAt(), request.getCreatedAt(), request.getUpdatedAt());
        }
    }
}
