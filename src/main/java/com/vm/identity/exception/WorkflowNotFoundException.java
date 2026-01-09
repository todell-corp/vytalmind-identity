package com.vm.identity.exception;

/**
 * Exception thrown when a workflow cannot be found.
 */
public class WorkflowNotFoundException extends RuntimeException {

    private final String workflowId;

    public WorkflowNotFoundException(String workflowId) {
        super("Workflow not found: " + workflowId);
        this.workflowId = workflowId;
    }

    public WorkflowNotFoundException(String workflowId, Throwable cause) {
        super("Workflow not found: " + workflowId, cause);
        this.workflowId = workflowId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
