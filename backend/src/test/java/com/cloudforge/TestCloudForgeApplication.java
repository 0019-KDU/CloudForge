package com.cloudforge;

import com.cloudforge.support.TestcontainersConfiguration;

import org.springframework.boot.SpringApplication;

/** Runs the application locally against a throwaway Testcontainers database (requires Docker). */
public class TestCloudForgeApplication {

    public static void main(String[] args) {
        SpringApplication.from(CloudForgeApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
