package com.cloudforge.provisioning.repository;

import java.util.List;
import java.util.UUID;

import com.cloudforge.provisioning.domain.ProvisioningAttempt;

import org.springframework.data.repository.Repository;

/** Insert-and-read only: the provisioning history has no update or delete path. */
public interface ProvisioningAttemptRepository extends Repository<ProvisioningAttempt, UUID> {

    ProvisioningAttempt save(ProvisioningAttempt entity);

    List<ProvisioningAttempt> findByJobIdOrderByStartedAtAsc(UUID jobId);
}
