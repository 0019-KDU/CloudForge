package com.cloudforge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html},
 * when {@code springdoc.api-docs.enabled=true} (the {@code local} profile). Off by default: the
 * full endpoint map should not be published from production without a decision to do so.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI cloudForgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudForge API")
                        .version("v1")
                        .description("Cloud resource provisioning and governance. Errors are RFC 9457 problem "
                                + "details with a stable `code`. Authenticate via POST /api/auth/login and send "
                                + "the access token as `Authorization: Bearer <token>`."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
