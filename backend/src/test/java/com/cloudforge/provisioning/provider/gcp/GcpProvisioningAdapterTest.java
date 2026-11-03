package com.cloudforge.provisioning.provider.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Completed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Failed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.InProgress;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.cloud.resourcemanager.v3.Project;
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class GcpProvisioningAdapterTest {

    private final UUID requestId = UUID.randomUUID();
    private final FakeGateway gateway = new FakeGateway();
    private final GcpProvisioningAdapter adapter = new GcpProvisioningAdapter(gateway);

    private ProvisioningCommand command() {
        return new ProvisioningCommand(requestId, "CF-2026-000007", ResourceTypeCode.GCP_PROJECT, Environment.DEVELOPMENT,
                JsonMapper.builder().build().readTree(
                        "{\"projectId\": \"acme-pay-dev\", \"displayName\": \"Payments dev\", \"folderId\": \"1001\"}"),
                "cloudforge-" + requestId);
    }

    private Project project(String requestLabel, Project.State state) {
        return Project.newBuilder().setName("projects/123456789").setProjectId("acme-pay-dev").setParent("folders/1001")
                .setState(state).putLabels(GcpProvisioningAdapter.REQUEST_ID_LABEL, requestLabel).build();
    }

    @Test
    void startsLongRunningCreationWithLabelsUnderTheFolder() {
        ProvisioningOutcome outcome = adapter.start(command());

        assertThat(outcome).isEqualTo(new InProgress("operations/cp.42", GcpProvisioningAdapter.POLL_INTERVAL));
        Project sent = gateway.created.getFirst();
        assertThat(sent.getParent()).isEqualTo("folders/1001");
        assertThat(sent.getDisplayName()).isEqualTo("Payments dev");
        assertThat(sent.getLabelsMap()).containsEntry("cloudforge-request-id", requestId.toString())
                .containsEntry("cloudforge-environment", "development");
    }

    @Test
    void pollsUntilTheOperationIsDone() {
        gateway.operation = Operation.newBuilder().setName("operations/cp.42").setDone(false).build();
        assertThat(adapter.poll(command(), "operations/cp.42")).isInstanceOf(InProgress.class);

        gateway.operation = Operation.newBuilder().setName("operations/cp.42").setDone(true)
                .setResponse(Any.pack(project(requestId.toString(), Project.State.ACTIVE))).build();
        Completed completed = (Completed) adapter.poll(command(), "operations/cp.42");

        assertThat(completed.externalResourceId()).isEqualTo("projects/123456789");
        assertThat(completed.details()).containsEntry("projectNumber", "123456789").containsEntry("projectId", "acme-pay-dev");
    }

    @Test
    void retryFindsTheProjectItCreatedEarlier() {
        gateway.existing = project(requestId.toString(), Project.State.ACTIVE);

        assertThat(adapter.start(command())).isInstanceOf(Completed.class);
        assertThat(gateway.created).isEmpty();
    }

    @Test
    void projectIdOwnedByAnyoneElseIsPermanentlyTaken() {
        gateway.existing = project(UUID.randomUUID().toString(), Project.State.ACTIVE);
        assertThat(((Failed) adapter.start(command())).code()).isEqualTo("PROJECT_ID_TAKEN");

        gateway.existing = null;
        gateway.operation = Operation.newBuilder().setDone(true)
                .setError(Status.newBuilder().setCode(Code.ALREADY_EXISTS_VALUE)).build();
        Failed failed = (Failed) adapter.poll(command(), "operations/cp.42");
        assertThat(failed.code()).isEqualTo("PROJECT_ID_TAKEN");
        assertThat(failed.retryable()).isFalse();
    }

    @Test
    void classifiesRpcErrors() {
        gateway.failure = exception(io.grpc.Status.Code.UNAVAILABLE);
        assertThat(((Failed) adapter.start(command())).retryable()).isTrue();

        gateway.failure = exception(io.grpc.Status.Code.PERMISSION_DENIED);
        Failed denied = (Failed) adapter.start(command());
        assertThat(denied.retryable()).isFalse();
        assertThat(denied.code()).isEqualTo("ACCESS_DENIED");
    }

    private static ApiException exception(io.grpc.Status.Code code) {
        return ApiExceptionFactory.createException("rpc failed", null, GrpcStatusCode.of(code), code == io.grpc.Status.Code.UNAVAILABLE);
    }

    private static final class FakeGateway implements GcpProjectsGateway {
        Project existing;
        Operation operation;
        ApiException failure;
        final List<Project> created = new ArrayList<>();

        @Override
        public Optional<Project> findProject(String projectId) {
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(existing);
        }

        @Override
        public String startCreate(Project project) {
            created.add(project);
            return "operations/cp.42";
        }

        @Override
        public Operation getOperation(String operationName) {
            return operation;
        }
    }
}
