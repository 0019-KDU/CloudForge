package com.cloudforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs: the provisioning scheduler (only with
 * {@code cloudforge.provisioning.worker.enabled=true}) and the daily data-retention cleanup.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
