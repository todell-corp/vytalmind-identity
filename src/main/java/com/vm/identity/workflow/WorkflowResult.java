package com.vm.identity.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Result wrapper for workflow executions to communicate success or failure
 * without throwing exceptions across the workflow boundary.
 *
 * @param <T> the type of the result value
 */
public record WorkflowResult<T>(
    T value,
    String errorCode,
    Map<String, String> errorDetails
) {
    @JsonIgnore
    public boolean isSuccess() {
        return errorCode == null;
    }

    public static <T> WorkflowResult<T> ok(T value) {
        return new WorkflowResult<>(value, null, null);
    }

    public static <T> WorkflowResult<T> error(String code) {
        return new WorkflowResult<>(null, code, null);
    }

    public static <T> WorkflowResult<T> error(String code, Map<String, String> details) {
        return new WorkflowResult<>(null, code, details);
    }
}
