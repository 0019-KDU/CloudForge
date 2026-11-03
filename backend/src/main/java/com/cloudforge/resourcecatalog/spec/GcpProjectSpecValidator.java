package com.cloudforge.resourcecatalog.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.cloudforge.common.error.FieldViolation;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Google Cloud project.
 *
 * <pre>
 * spec:        { "projectId": "acme-payments-dev", "displayName": "Payments dev", "folderId": "123456789012" }
 * constraints: { "allowedFolderIds": ["123456789012"] }
 * </pre>
 *
 * Project ID rules from the Resource Manager documentation: 6 to 30 characters, lowercase letters,
 * digits and hyphens, starting with a letter and not ending with a hyphen. Display name: 4 to 30
 * characters of letters, digits, single quotes, hyphens, spaces or exclamation points. Project IDs
 * are globally unique and cannot be reused after deletion; uniqueness is checked at provisioning time.
 */
@Component
class GcpProjectSpecValidator implements ResourceSpecValidator {

    private static final Pattern PROJECT_ID = Pattern.compile("^[a-z][a-z0-9-]{4,28}[a-z0-9]$");
    private static final Pattern DISPLAY_NAME = Pattern.compile("^[A-Za-z0-9' !-]{4,30}$");
    private static final Pattern FOLDER_ID = Pattern.compile("^[0-9]{1,20}$");

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.GCP_PROJECT;
    }

    @Override
    public List<FieldViolation> validateSpec(JsonNode spec, JsonNode constraints) {
        List<FieldViolation> violations = new ArrayList<>();
        JsonFields fields = JsonFields.of(spec, "spec", violations)
                .allowOnly(Set.of("projectId", "displayName", "folderId"));

        String projectId = fields.requiredText("projectId", PROJECT_ID,
                "must be 6-30 characters of lowercase letters, digits and hyphens, start with a letter and not end with a hyphen");
        if (projectId != null && projectId.contains("google")) {
            fields.violate("projectId", "must not contain the reserved word 'google'");
        }
        fields.optionalText("displayName", DISPLAY_NAME,
                "must be 4-30 characters of letters, digits, spaces, hyphens, single quotes or exclamation points");

        String folderId = fields.requiredText("folderId", FOLDER_ID, "must be a numeric folder ID");
        List<String> allowedFolders = JsonFields.of(constraints, "constraints", new ArrayList<>())
                .optionalTextList("allowedFolderIds", FOLDER_ID, "must be a numeric folder ID");
        if (folderId != null && !allowedFolders.contains(folderId)) {
            fields.violate("folderId", "is not an approved folder for this environment");
        }
        return violations;
    }

    @Override
    public List<FieldViolation> validateConstraints(JsonNode constraints, boolean allowed) {
        List<FieldViolation> violations = new ArrayList<>();
        JsonFields fields = JsonFields.of(constraints, "constraints", violations).allowOnly(Set.of("allowedFolderIds"));
        List<String> folders = fields.optionalTextList("allowedFolderIds", FOLDER_ID, "must be a numeric folder ID");
        if (allowed && folders.isEmpty() && violations.isEmpty()) {
            fields.violate("allowedFolderIds", "must list at least one folder when the policy allows requests");
        }
        return violations;
    }
}
