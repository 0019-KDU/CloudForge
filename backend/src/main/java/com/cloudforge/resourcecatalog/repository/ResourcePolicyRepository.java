package com.cloudforge.resourcecatalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourcePolicy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourcePolicyRepository extends JpaRepository<ResourcePolicy, UUID> {

    Optional<ResourcePolicy> findByResourceTypeIdAndEnvironment(UUID resourceTypeId, Environment environment);

    /** All policies of several types in one query, for catalog listings without N+1. */
    List<ResourcePolicy> findByResourceTypeIdIn(Collection<UUID> resourceTypeIds);
}
