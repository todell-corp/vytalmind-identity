package com.vm.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for workflow status queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStatusResponse {

    private String workflowId;
    private String runId;
    private String workflowType;
    private ExecutionStatus status;
    private String message;
    private List<StepStatus> steps;
    private Map<String, Object> result;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMillis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepStatus {
        private int stepNumber;
        private String stepName;
        private String service;
        private StepState state;
        private String message;
        private Instant executedAt;
        private Long durationMillis;

        public enum StepState {
            PENDING,
            IN_PROGRESS,
            COMPLETED,
            FAILED,
            COMPENSATED,
            SKIPPED
        }
    }

    public enum ExecutionStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TERMINATED,
        CONTINUED_AS_NEW,
        TIMED_OUT
    }
}
