package com.cloudforge.provisioning.repository;

import java.util.Optional;
import java.util.UUID;

import com.cloudforge.provisioning.domain.ProvisioningResult;

import org.springframework.data.repository.Repository;

/** Insert-and-read only: the provisioning history has no update or delete path. */
public interface ProvisioningResultRepository extends Repository<ProvisioningResult, UUID> {

    ProvisioningResult save(ProvisioningResult entity);

    Optional<ProvisioningResult> findByRequestId(UUID requestId);
}
