package com.cloudforge.resourcecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.support.TestUsers;
import com.cloudforge.support.TestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The catalog against real PostgreSQL. The catalog is global state shared by all integration tests,
 * so each test works on its own type/environment pair and restores what it disables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ResourceCatalogIT {

    private static final String NAMESPACE_LIMITS = "{\"maxCpuCores\": 4, \"maxMemoryGi\": 16}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;

    User admin;
    User engineer;

    @BeforeEach
    void users() {
        admin = testUsers.create(Role.PLATFORM_ADMIN);
        engineer = testUsers.create(Role.ENGINEER);
    }

    @Test
    void seededTypesAreListedForAnyAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/resource-types").with(testUsers.bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("AWS_ACCOUNT")))
                .andExpect(jsonPath("$[*].code").value(hasItem("GCP_PROJECT")))
                .andExpect(jsonPath("$[*].code").value(hasItem("K8S_NAMESPACE")))
                .andExpect(jsonPath("$[0].constraints").doesNotExist());

        mockMvc.perform(get("/api/resource-types")).andExpect(status().isUnauthorized());
    }

    @Test
    void environmentsAreDeniedUntilAdminAllowsThem() throws Exception {
        validateSpec("GCP_PROJECT", "STAGING", "{\"projectId\": \"acme-stg\", \"folderId\": \"42\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_NOT_ALLOWED"));

        putPolicy("GCP_PROJECT", "STAGING", "{\"allowed\": true, \"constraints\": {\"allowedFolderIds\": [\"42\"]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        validateSpec("GCP_PROJECT", "STAGING", "{\"projectId\": \"acme-stg\", \"folderId\": \"42\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(get("/api/resource-types/GCP_PROJECT").with(testUsers.bearer(engineer)))
                .andExpect(jsonPath("$.allowedEnvironments").value(hasItem("STAGING")));
    }

    @Test
    void invalidSpecReportsEveryFieldWithPaths() throws Exception {
        putPolicy("K8S_NAMESPACE", "DEVELOPMENT", "{\"allowed\": true, \"constraints\": " + NAMESPACE_LIMITS + "}")
                .andExpect(status().isOk());

        validateSpec("K8S_NAMESPACE", "DEVELOPMENT",
                "{\"namespace\": \"kube-system\", \"cpuCores\": 64, \"hostNetwork\": true}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_SPEC"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("spec.namespace")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("spec.cpuCores")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("spec.memoryGi")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("spec.hostNetwork")));
    }

    @Test
    void allowingPolicyWithoutPlacementRestrictionsIsRejected() throws Exception {
        putPolicy("AWS_ACCOUNT", "PRODUCTION", "{\"allowed\": true, \"constraints\": {}}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_POLICY_CONSTRAINTS"))
                .andExpect(jsonPath("$.errors[0].field").value("constraints.allowedOrganizationalUnitIds"));
    }

    @Test
    void constraintsAreStoredAsJsonb() throws Exception {
        putPolicy("K8S_NAMESPACE", "STAGING", "{\"allowed\": false, \"maxActivePerUser\": 3, \"constraints\": "
                + NAMESPACE_LIMITS + "}").andExpect(status().isOk());

        String stored = jdbc.queryForObject("""
                SELECT p.constraints ->> 'maxMemoryGi' FROM resource_policy p
                JOIN resource_type t ON t.id = p.resource_type_id
                WHERE t.code = 'K8S_NAMESPACE' AND p.environment = 'STAGING'""", String.class);
        assertThat(stored).isEqualTo("16");
    }

    @Test
    void disabledTypeDisappearsForUsersButNotForAdmins() throws Exception {
        try {
            updateType("AWS_ACCOUNT", false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

            mockMvc.perform(get("/api/resource-types").with(testUsers.bearer(engineer)))
                    .andExpect(jsonPath("$[*].code").value(not(hasItem("AWS_ACCOUNT"))));
            mockMvc.perform(get("/api/resource-types/AWS_ACCOUNT").with(testUsers.bearer(engineer)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/admin/resource-types/AWS_ACCOUNT").with(testUsers.bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.policies.length()").value(3));
        } finally {
            updateType("AWS_ACCOUNT", true).andExpect(status().isOk());
        }
    }

    @Test
    void onlyAdminsCanChangeTheCatalog() throws Exception {
        mockMvc.perform(put("/api/admin/resource-types/K8S_NAMESPACE/policies/PRODUCTION").with(testUsers.bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownResourceTypeIsA400() throws Exception {
        mockMvc.perform(get("/api/resource-types/AZURE_SUBSCRIPTION").with(testUsers.bearer(engineer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiDocsAreDisabledOutsideTheLocalProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    private ResultActions putPolicy(String type, String environment, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/resource-types/{code}/policies/{env}", type, environment)
                .with(testUsers.bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions validateSpec(String type, String environment, String spec) throws Exception {
        return mockMvc.perform(post("/api/resource-types/{code}/spec-validation", type).with(testUsers.bearer(engineer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"environment\": \"" + environment + "\", \"spec\": " + spec + "}"));
    }

    private ResultActions updateType(String type, boolean enabled) throws Exception {
        return mockMvc.perform(put("/api/admin/resource-types/{code}", type).with(testUsers.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"AWS account\", \"description\": \"A new member account.\", \"enabled\": "
                        + enabled + "}"));
    }
}
