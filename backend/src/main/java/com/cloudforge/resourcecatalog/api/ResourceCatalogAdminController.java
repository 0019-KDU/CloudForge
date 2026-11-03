package com.cloudforge.resourcecatalog.api;

import java.util.List;

import jakarta.validation.Valid;

import com.cloudforge.common.api.ETags;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.resourcecatalog.api.CatalogDtos.AdminResourceTypeResponse;
import com.cloudforge.resourcecatalog.api.CatalogDtos.PolicyResponse;
import com.cloudforge.resourcecatalog.api.CatalogDtos.UpdatePolicyRequest;
import com.cloudforge.resourcecatalog.api.CatalogDtos.UpdateResourceTypeRequest;
import com.cloudforge.resourcecatalog.application.ResourceCatalogService;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/resource-types")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class ResourceCatalogAdminController {

    private final ResourceCatalogService catalog;

    ResourceCatalogAdminController(ResourceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<AdminResourceTypeResponse> list() {
        return catalog.listCatalog(true).stream().map(AdminResourceTypeResponse::from).toList();
    }

    @GetMapping("/{code}")
    ResponseEntity<AdminResourceTypeResponse> get(@PathVariable ResourceTypeCode code) {
        return withETag(AdminResourceTypeResponse.from(catalog.getEntry(code, true)));
    }

    /** If-Match applies to the type's version (ETag of GET/PUT on the type). */
    @PutMapping("/{code}")
    ResponseEntity<AdminResourceTypeResponse> update(CurrentUser actor, @PathVariable ResourceTypeCode code,
            @Valid @RequestBody UpdateResourceTypeRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        catalog.updateType(actor.id(), code, request.displayName(), request.description(), request.enabled(),
                ETags.parseIfMatch(ifMatch));
        return withETag(AdminResourceTypeResponse.from(catalog.getEntry(code, true)));
    }

    /** If-Match applies to the policy's own version (the {@code version} field of each policy). */
    @PutMapping("/{code}/policies/{environment}")
    ResponseEntity<PolicyResponse> updatePolicy(CurrentUser actor, @PathVariable ResourceTypeCode code,
            @PathVariable Environment environment, @Valid @RequestBody UpdatePolicyRequest request, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        PolicyResponse policy = PolicyResponse.from(catalog.updatePolicy(actor.id(), code, environment,
                request.allowed(), request.maxActivePerUser(), request.constraints(), ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.ok().eTag(ETags.of(policy.version())).body(policy);
    }

    private static ResponseEntity<AdminResourceTypeResponse> withETag(AdminResourceTypeResponse type) {
        return ResponseEntity.ok().eTag(ETags.of(type.version())).body(type);
    }
}
