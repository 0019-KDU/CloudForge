package com.cloudforge.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cloudforge.common.error.BusinessRuleViolationException;
import com.cloudforge.common.web.ProblemDetailSecurityHandler;
import com.cloudforge.config.JwtConfig;
import com.cloudforge.config.SecurityConfig;
import com.cloudforge.config.TimeConfig;
import com.cloudforge.identity.application.PasswordResetService;
import com.cloudforge.identity.application.UserAdministrationService;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserAdminController.class)
@Import({SecurityConfig.class, JwtConfig.class, ProblemDetailSecurityHandler.class, TimeConfig.class})
class UserAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserAdministrationService users;

    @MockitoBean
    PasswordResetService passwordResets;

    private static final UUID ADMIN_ID = UUID.randomUUID();

    private static JwtRequestPostProcessor as(String... roles) {
        SimpleGrantedAuthority[] authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(token -> token.subject(ADMIN_ID.toString())).authorities(authorities);
    }

    @Test
    void selfLockoutAttemptPassesTheActorFromTheToken() throws Exception {
        when(users.replaceRoles(any(), any(), any(), any())).thenThrow(
                new BusinessRuleViolationException("SELF_LOCKOUT", "no"));

        mockMvc.perform(put("/api/admin/users/{id}/roles", ADMIN_ID).with(as("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ENGINEER\"]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
        verify(users).replaceRoles(ADMIN_ID, ADMIN_ID, Set.of(Role.ENGINEER), null);
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void nonAdminRolesGet403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(as("ENGINEER", "MANAGER", "AUDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(users);
    }

    @Test
    void adminListsUsersWithoutPasswordHashes() throws Exception {
        User user = new User("eng@example.com", "Eng", "$2a$10$secret-hash", Set.of(Role.ENGINEER));
        when(users.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/admin/users").with(as("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("eng@example.com"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("ENGINEER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(content().string(not(containsString("secret-hash"))));
    }

    @Test
    void sortingBySensitiveFieldIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(as("PLATFORM_ADMIN")).param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SORT"));
        verifyNoInteractions(users);
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/roles", UUID.randomUUID()).with(as("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test
    void emptyRoleSetIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/roles", UUID.randomUUID()).with(as("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
