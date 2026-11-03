package com.cloudforge.provisioning.provider.gcp;

import java.util.Optional;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.resourcemanager.v3.Project;
import com.google.longrunning.Operation;

/**
 * The three Resource Manager calls the adapter needs. It separates gRPC and long-running-operation
 * plumbing from the provisioning decisions, so {@link GcpProvisioningAdapter} can be tested with
 * plain proto objects.
 */
interface GcpProjectsGateway {

    /**
     * Empty when the project does not exist <em>or</em> the caller may not see it: Resource Manager
     * answers PERMISSION_DENIED for nonexistent projects, so the two cannot be told apart here.
     */
    Optional<Project> findProject(String projectId) throws ApiException;

    /** Starts project creation and returns the long-running operation name. */
    String startCreate(Project project) throws ApiException;

    Operation getOperation(String operationName) throws ApiException;
}
