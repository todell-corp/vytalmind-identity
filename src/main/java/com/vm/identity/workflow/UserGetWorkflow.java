package com.vm.identity.workflow;

import com.vm.identity.entity.User;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface UserGetWorkflow {

    @WorkflowMethod
    User getUser(String userId);
}
