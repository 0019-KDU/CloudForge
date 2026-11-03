package com.cloudforge.resourcecatalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.resourcecatalog.domain.ResourceType;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, UUID> {

    Optional<ResourceType> findByCode(ResourceTypeCode code);

    List<ResourceType> findAllByOrderByDisplayNameAsc();

    List<ResourceType> findByEnabledTrueOrderByDisplayNameAsc();
}
