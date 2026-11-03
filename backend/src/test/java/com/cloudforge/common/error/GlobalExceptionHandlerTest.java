package com.cloudforge.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.cloudforge.common.web.CorrelationIdFilter;
import com.cloudforge.common.web.ProblemDetailSecurityHandler;
import com.cloudforge.config.JwtConfig;
import com.cloudforge.config.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import({SecurityConfig.class, JwtConfig.class, ProblemDetailSecurityHandler.class, GlobalExceptionHandlerTest.FixedClock.class,
        GlobalExceptionHandlerTest.FailingController.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvc mockMvc;

    @Test
    void notFoundMapsTo404WithCodeCorrelationIdAndTimestamp() throws Exception {
        mockMvc.perform(get("/test/not-found").header(CorrelationIdFilter.HEADER, "trace-abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Request was not found."))
                .andExpect(jsonPath("$.correlationId").value("trace-abc-123"))
                .andExpect(jsonPath("$.timestamp").value("2026-01-01T00:00:00Z"));
    }

    @Test
    void businessRuleViolationMapsTo422() throws Exception {
        mockMvc.perform(get("/test/rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void validationFailureListsFieldsWithoutEchoingValues() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"secret\":\"hunter2-is-too-long-for-this-field\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void malformedJsonIsA400Problem() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void unexpectedExceptionIsGeneric500WithoutInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("db-password"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void invalidBearerTokenIsA401Problem() throws Exception {
        mockMvc.perform(get("/test/not-found").with(anonymous()).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void anonymousRequestIsA401Problem() throws Exception {
        mockMvc.perform(get("/test/not-found").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("REQUEST_NOT_FOUND", "Request was not found.");
        }

        @GetMapping("/test/rule")
        void rule() {
            throw new BusinessRuleViolationException("SELF_APPROVAL_FORBIDDEN", "You cannot approve your own request.");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("DUPLICATE", "Already exists.");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("connection refused for db-password=xyz");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody Payload payload) {
        }

        record Payload(@NotBlank String name, @Size(max = 10) String secret) {
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
