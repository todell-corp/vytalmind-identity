package com.vm.identity.workflow;

import com.vm.identity.dto.UserUpdateRequest;
import com.vm.identity.entity.User;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface UserUpdateWorkflow {

    @WorkflowMethod
    WorkflowResult<User> updateUser(String userId, UserUpdateRequest request);
}
