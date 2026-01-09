package com.vm.identity.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Result wrapper for workflow executions to communicate success or failure
 * without throwing exceptions across the workflow boundary.
 *
 * @param <T> the type of the result value
 */
public record WorkflowResult<T>(
    T value,
    String errorCode
) {
    @JsonIgnore
    public boolean isSuccess() {
        return errorCode == null;
    }

    public static <T> WorkflowResult<T> ok(T value) {
        return new WorkflowResult<>(value, null);
    }

    public static <T> WorkflowResult<T> error(String code) {
        return new WorkflowResult<>(null, code);
    }
}
