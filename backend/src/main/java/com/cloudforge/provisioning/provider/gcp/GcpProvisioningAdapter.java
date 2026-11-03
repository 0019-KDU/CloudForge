package com.cloudforge.provisioning.provider.gcp;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.provisioning.provider.ProviderMode;
import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.resourcemanager.v3.Project;
import com.google.longrunning.Operation;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;

import tools.jackson.databind.JsonNode;

/**
 * Creates Google Cloud projects through Resource Manager v3.
 *
 * <p>{@code projects.create} is a long-running operation: {@link #start} returns its name and
 * {@link #poll} follows it until it is done. Ownership is recorded in the
 * {@code cloudforge-request-id} label. A project ID that already exists is completed only if it
 * carries this request's label (an earlier attempt created it); otherwise the ID is taken. Project
 * IDs are globally unique and cannot be reused, so that is a permanent failure.
 */
public class GcpProvisioningAdapter implements CloudProvisioningProvider {

    static final String REQUEST_ID_LABEL = "cloudforge-request-id";
    static final Duration POLL_INTERVAL = Duration.ofSeconds(10);

    private final GcpProjectsGateway gateway;

    GcpProvisioningAdapter(GcpProjectsGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.GCP_PROJECT;
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.REAL;
    }

    @Override
    public ProvisioningOutcome start(ProvisioningCommand command) {
        JsonNode spec = command.spec();
        String projectId = spec.get("projectId").stringValue();
        try {
            Optional<Project> existing = gateway.findProject(projectId);
            if (existing.isPresent()) {
                return fromExisting(existing.get(), command);
            }
            Project project = Project.newBuilder()
                    .setProjectId(projectId)
                    .setDisplayName(spec.hasNonNull("displayName") ? spec.get("displayName").stringValue() : projectId)
                    .setParent("folders/" + spec.get("folderId").stringValue())
                    .putLabels("managed-by", "cloudforge")
                    .putLabels(REQUEST_ID_LABEL, command.requestId().toString())
                    .putLabels("cloudforge-environment", command.environment().name().toLowerCase(Locale.ROOT))
                    .build();
            return new ProvisioningOutcome.InProgress(gateway.startCreate(project), POLL_INTERVAL);
        } catch (ApiException e) {
            return classify(e);
        }
    }

    @Override
    public ProvisioningOutcome poll(ProvisioningCommand command, String operationName) {
        try {
            Operation operation = gateway.getOperation(operationName);
            if (!operation.getDone()) {
                return new ProvisioningOutcome.InProgress(operationName, POLL_INTERVAL);
            }
            if (operation.hasError()) {
                // A finished operation is final: polling it again returns the same error, so do not retry it.
                Code code = Code.forNumber(operation.getError().getCode());
                return code == Code.ALREADY_EXISTS
                        ? projectIdTaken(command)
                        : ProvisioningOutcome.Failed.permanent("OPERATION_FAILED",
                                "Google Cloud could not create the project (" + code + ").");
            }
            return completed(operation.getResponse().unpack(Project.class));
        } catch (ApiException e) {
            return classify(e);
        } catch (InvalidProtocolBufferException e) {
            return ProvisioningOutcome.Failed.retryable("PROVIDER_ERROR", "Unexpected response from Google Cloud.");
        }
    }

    private ProvisioningOutcome fromExisting(Project project, ProvisioningCommand command) {
        if (!command.requestId().toString().equals(project.getLabelsMap().get(REQUEST_ID_LABEL))) {
            return projectIdTaken(command);
        }
        if (project.getState() != Project.State.ACTIVE) {
            return ProvisioningOutcome.Failed.permanent("PROJECT_NOT_ACTIVE",
                    "Project '" + project.getProjectId() + "' exists but is in state " + project.getState() + ".");
        }
        return completed(project);
    }

    private static ProvisioningOutcome completed(Project project) {
        String name = project.getName(); // projects/<number>
        return new ProvisioningOutcome.Completed(name, Map.of(
                "projectId", project.getProjectId(),
                "projectNumber", name.substring(name.lastIndexOf('/') + 1),
                "parent", project.getParent()));
    }

    private static ProvisioningOutcome projectIdTaken(ProvisioningCommand command) {
        return ProvisioningOutcome.Failed.permanent("PROJECT_ID_TAKEN", "The project ID '"
                + command.spec().get("projectId").stringValue() + "' is already in use. Project IDs are globally unique.");
    }

    static ProvisioningOutcome classify(ApiException e) {
        StatusCode.Code code = e.getStatusCode().getCode();
        return switch (code) {
            case UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, ABORTED, UNKNOWN, RESOURCE_EXHAUSTED, CANCELLED ->
                    ProvisioningOutcome.Failed.retryable("PROVIDER_UNAVAILABLE",
                            "Google Cloud is temporarily unavailable (" + code + ").");
            case PERMISSION_DENIED, UNAUTHENTICATED -> ProvisioningOutcome.Failed.permanent("ACCESS_DENIED",
                    "CloudForge is not allowed to create projects in this folder (" + code + ").");
            case ALREADY_EXISTS -> ProvisioningOutcome.Failed.permanent("PROJECT_ID_TAKEN",
                    "The project ID is already in use. Project IDs are globally unique.");
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE, NOT_FOUND ->
                    ProvisioningOutcome.Failed.permanent("INVALID_REQUEST", "Google Cloud rejected the request (" + code + ").");
            default -> ProvisioningOutcome.Failed.permanent("PROVIDER_ERROR", "Unexpected Google Cloud error (" + code + ").");
        };
    }
}
