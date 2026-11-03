package com.cloudforge.provisioning.provider.gcp;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.cloud.resourcemanager.v3.CreateProjectRequest;
import com.google.cloud.resourcemanager.v3.Project;
import com.google.cloud.resourcemanager.v3.ProjectsClient;
import com.google.longrunning.Operation;

/** {@link GcpProjectsGateway} backed by the Cloud Resource Manager v3 client. */
class ResourceManagerGateway implements GcpProjectsGateway, AutoCloseable {

    private final ProjectsClient client;

    ResourceManagerGateway(ProjectsClient client) {
        this.client = client;
    }

    @Override
    public Optional<Project> findProject(String projectId) {
        try {
            return Optional.of(client.getProject("projects/" + projectId));
        } catch (NotFoundException | PermissionDeniedException e) {
            return Optional.empty();
        }
    }

    @Override
    public String startCreate(Project project) {
        try {
            // Blocks only until the operation is accepted, not until the project exists.
            return client.createProjectAsync(CreateProjectRequest.newBuilder().setProject(project).build()).getName();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException apiException) {
                throw apiException;
            }
            throw new IllegalStateException("Project creation could not be started", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting project creation", e);
        }
    }

    @Override
    public Operation getOperation(String operationName) {
        return client.getOperationsClient().getOperation(operationName);
    }

    @Override
    public void close() {
        client.close();
    }
}
