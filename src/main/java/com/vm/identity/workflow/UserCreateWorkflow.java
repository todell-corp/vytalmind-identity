package com.vm.identity.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface UserCreateWorkflow {

    @WorkflowMethod
    String createUser(String userId, String username, String email, String password, String firstName, String lastName);
}
