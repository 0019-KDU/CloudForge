package com.cloudforge.resourcecatalog.api;

import java.util.List;

import jakarta.validation.Valid;

import com.cloudforge.resourcecatalog.api.CatalogDtos.ResourceTypeResponse;
import com.cloudforge.resourcecatalog.api.CatalogDtos.SpecValidationRequest;
import com.cloudforge.resourcecatalog.api.CatalogDtos.SpecValidationResponse;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The catalog as requesters see it: enabled types and where they may be requested. */
@RestController
@RequestMapping("/api/resource-types")
class ResourceTypeController {

    private final ResourceCatalogService catalog;

    ResourceTypeController(ResourceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<ResourceTypeResponse> list() {
        return catalog.listCatalog(false).stream().map(ResourceTypeResponse::from).toList();
    }

    @GetMapping("/{code}")
    ResourceTypeResponse get(@PathVariable ResourceTypeCode code) {
        return ResourceTypeResponse.from(catalog.getEntry(code, false));
    }

    /**
     * Checks a draft specification without creating anything, so a client can show field errors
     * before submitting. Responds 200 when valid, otherwise 422 with the field violations.
     */
    @PostMapping("/{code}/spec-validation")
    SpecValidationResponse validate(@PathVariable ResourceTypeCode code, @Valid @RequestBody SpecValidationRequest request) {
        catalog.checkRequestable(code, request.environment(), request.spec());
        return new SpecValidationResponse(true);
    }
}
