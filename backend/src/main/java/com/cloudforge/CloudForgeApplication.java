package com.cloudforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * CloudForge modular monolith entry point.
 *
 * <p>The default in-memory user that Spring Boot generates is excluded: CloudForge never runs
 * with an implicit account. Real users come from the identity module.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CloudForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudForgeApplication.class, args);
    }
}
