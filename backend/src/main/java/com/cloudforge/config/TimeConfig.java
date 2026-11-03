package com.cloudforge.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single UTC {@link Clock} for the whole application. Business code takes the clock as a
 * dependency instead of calling {@code Instant.now()}, so time-dependent rules (retry backoff,
 * expiry, decision timestamps) are deterministic in tests.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
