package com.vm.identity.config;

import io.temporal.serviceclient.WorkflowServiceStubs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for health check indicators.
 */
@Configuration
public class HealthCheckConfig {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckConfig.class);

    @Bean
    public HealthIndicator temporalHealthIndicator(WorkflowServiceStubs workflowServiceStubs) {
        return () -> {
            try {
                // Check if we can reach the Temporal service
                workflowServiceStubs.blockingStub()
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .getSystemInfo(io.temporal.api.workflowservice.v1.GetSystemInfoRequest.getDefaultInstance());
                return Health.up()
                        .withDetail("service", "Temporal")
                        .withDetail("status", "Connected")
                        .build();
            } catch (Exception e) {
                log.warn("Temporal health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("service", "Temporal")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}
