package com.cloudforge.resourcecatalog.spec;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.stereotype.Component;

/** Looks up the validator for a type. Fails at startup if any type has no validator, or has two. */
@Component
public class ResourceSpecValidators {

    private final Map<ResourceTypeCode, ResourceSpecValidator> byType = new EnumMap<>(ResourceTypeCode.class);

    public ResourceSpecValidators(List<ResourceSpecValidator> validators) {
        for (ResourceSpecValidator validator : validators) {
            if (byType.put(validator.type(), validator) != null) {
                throw new IllegalStateException("Duplicate spec validator for " + validator.type());
            }
        }
        for (ResourceTypeCode type : ResourceTypeCode.values()) {
            if (!byType.containsKey(type)) {
                throw new IllegalStateException("No spec validator for resource type " + type);
            }
        }
    }

    public ResourceSpecValidator forType(ResourceTypeCode type) {
        return byType.get(type);
    }
}
