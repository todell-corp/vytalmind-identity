package com.vm.identity.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for metrics and observability.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public WorkflowMetrics workflowMetrics(MeterRegistry registry) {
        return new WorkflowMetrics(registry);
    }

    /**
     * Custom metrics for workflow operations.
     */
    public static class WorkflowMetrics {
        private final MeterRegistry registry;
        private final Timer orderWorkflowTimer;

        public WorkflowMetrics(MeterRegistry registry) {
            this.registry = registry;
            this.orderWorkflowTimer = Timer.builder("vytalmind_identity.workflow.order.duration")
                    .description("Duration of order workflow executions")
                    .register(registry);
        }

        public void recordOrderWorkflowStart() {
            registry.counter("vytalmind_identity.workflow.order.started").increment();
        }

        public void recordOrderWorkflowComplete(boolean success) {
            registry.counter("vytalmind_identity.workflow.order.completed",
                    "success", String.valueOf(success)).increment();
        }

        public void recordActivityExecution(String activityName, String serviceName, boolean success, long durationMs) {
            registry.counter("vytalmind_identity.activity.executions",
                    "activity", activityName,
                    "service", serviceName,
                    "success", String.valueOf(success)).increment();

            registry.timer("vytalmind_identity.activity.duration",
                    "activity", activityName,
                    "service", serviceName)
                    .record(java.time.Duration.ofMillis(durationMs));
        }

        public void recordCompensation(String stepName, boolean success) {
            registry.counter("vytalmind_identity.compensation.executions",
                    "step", stepName,
                    "success", String.valueOf(success)).increment();
        }

        public Timer getOrderWorkflowTimer() {
            return orderWorkflowTimer;
        }
    }
}
