package com.cloudforge.resourcecatalog.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.cloudforge.common.error.FieldViolation;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * AWS member account, created through AWS Organizations.
 *
 * <pre>
 * spec:        { "accountName": "payments-dev", "email": "aws-payments-dev@acme.com",
 *                "organizationalUnitId": "ou-ab12-cd34ef56" }
 * constraints: { "allowedOrganizationalUnitIds": ["ou-ab12-cd34ef56"], "allowedEmailDomains": ["acme.com"] }
 * </pre>
 *
 * Limits from the Organizations {@code CreateAccount} API: account name 1 to 50 characters, email 6
 * to 64 characters. The OU ID format follows the Organizations API pattern. The root email must be
 * unique across all of AWS; that can only be checked by the provider at provisioning time.
 */
@Component
class AwsAccountSpecValidator implements ResourceSpecValidator {

    private static final Pattern ACCOUNT_NAME = Pattern.compile("^[\\p{Print}&&[^\\p{Cntrl}]]{1,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern OU_ID = Pattern.compile("^ou-[0-9a-z]{4,32}-[a-z0-9]{8,32}$");
    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9.-]+\\.[a-z]{2,}$");

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.AWS_ACCOUNT;
    }

    @Override
    public List<FieldViolation> validateSpec(JsonNode spec, JsonNode constraints) {
        List<FieldViolation> violations = new ArrayList<>();
        JsonFields fields = JsonFields.of(spec, "spec", violations)
                .allowOnly(Set.of("accountName", "email", "organizationalUnitId"));
        JsonFields limits = JsonFields.of(constraints, "constraints", new ArrayList<>());

        fields.requiredText("accountName", ACCOUNT_NAME, "must be 1-50 printable characters");

        String email = fields.requiredText("email", EMAIL, "must be a valid email address");
        if (email != null && (email.length() < 6 || email.length() > 64)) {
            fields.violate("email", "must be 6-64 characters long");
        } else if (email != null) {
            List<String> domains = limits.optionalTextList("allowedEmailDomains", DOMAIN, "must be a domain name");
            String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
            if (!domains.isEmpty() && !domains.contains(domain)) {
                fields.violate("email", "must use one of the approved email domains");
            }
        }

        String ou = fields.requiredText("organizationalUnitId", OU_ID, "must be an organizational unit ID such as ou-ab12-cd34ef56");
        List<String> allowedOus = limits.optionalTextList("allowedOrganizationalUnitIds", OU_ID, "must be an organizational unit ID");
        if (ou != null && !allowedOus.contains(ou)) {
            fields.violate("organizationalUnitId", "is not an approved organizational unit for this environment");
        }
        return violations;
    }

    @Override
    public List<FieldViolation> validateConstraints(JsonNode constraints, boolean allowed) {
        List<FieldViolation> violations = new ArrayList<>();
        JsonFields fields = JsonFields.of(constraints, "constraints", violations)
                .allowOnly(Set.of("allowedOrganizationalUnitIds", "allowedEmailDomains"));
        List<String> ous = fields.optionalTextList("allowedOrganizationalUnitIds", OU_ID, "must be an organizational unit ID");
        fields.optionalTextList("allowedEmailDomains", DOMAIN, "must be a lowercase domain name");
        if (allowed && ous.isEmpty() && violations.isEmpty()) {
            fields.violate("allowedOrganizationalUnitIds", "must list at least one OU when the policy allows requests");
        }
        return violations;
    }
}
