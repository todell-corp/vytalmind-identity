package com.vm.identity.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface UserDeleteWorkflow {

    @WorkflowMethod
    String deleteUser(String userId);
}
