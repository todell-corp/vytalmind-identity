package com.vm.identity.service;

import com.vm.identity.dto.UserCreateRequest;
import com.vm.identity.dto.UserResponse;
import com.vm.identity.dto.UserUpdateRequest;
import com.vm.identity.entity.User;
import com.vm.identity.exception.ApplicationFailureHandler;
import com.vm.identity.workflow.UserCreateWorkflow;
import com.vm.identity.workflow.UserDeleteWorkflow;
import com.vm.identity.workflow.UserGetWorkflow;
import com.vm.identity.workflow.UserUpdateWorkflow;
import com.vm.identity.workflow.WorkflowResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public UserService(WorkflowClient workflowClient,
            @Value("${temporal.worker.task-queue}") String taskQueue) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating user with username: {}", request.getUsername());

        UUID userId = UUID.randomUUID();
        String workflowId = "user-create-" + request.getUsername() + "-" + UUID.randomUUID();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(5))
                .build();

        UserCreateWorkflow workflow = workflowClient.newWorkflowStub(UserCreateWorkflow.class, options);

        WorkflowResult<String> result = workflow.createUser(
                userId.toString(),
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName());

        if (!result.isSuccess()) {
            throw ApplicationFailureHandler.mapErrorCode(result.errorCode(), request.getUsername());
        }

        UserResponse response = new UserResponse();
        response.setUserId(result.value());
        response.setUsername(request.getUsername());
        response.setEmail(request.getEmail());
        response.setFirstName(request.getFirstName());
        response.setLastName(request.getLastName());
        response.setStatus("User created successfully");
        response.setWorkflowId(workflowId);

        log.info("User created successfully with userId: {}, username: {}", result.value(), request.getUsername());
        return response;
    }

    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user: {}", userId);

        String workflowId = "user-update-" + userId + "-" + UUID.randomUUID();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(5))
                .build();

        UserUpdateWorkflow workflow = workflowClient.newWorkflowStub(UserUpdateWorkflow.class, options);

        WorkflowResult<User> result = workflow.updateUser(userId, request);

        if (!result.isSuccess()) {
            throw ApplicationFailureHandler.mapErrorCode(result.errorCode(), userId);
        }

        User updatedUser = result.value();
        log.info("Received user from workflow: id={}, username={}, email={}, firstName={}, lastName={}, idpId={}",
                updatedUser.getId(), updatedUser.getUsername(), updatedUser.getEmail(),
                updatedUser.getFirstName(), updatedUser.getLastName(), updatedUser.getIdpId());

        UserResponse response = new UserResponse();
        response.setUserId(updatedUser.getId().toString());
        response.setUsername(updatedUser.getUsername());
        response.setEmail(updatedUser.getEmail());
        response.setFirstName(updatedUser.getFirstName());
        response.setLastName(updatedUser.getLastName());
        response.setStatus("User updated successfully");
        response.setWorkflowId(workflowId);

        log.info("User updated successfully: {} (username: {})", userId, updatedUser.getUsername());
        return response;
    }

    public UserResponse getUser(String userId) {
        log.info("Getting user: {}", userId);

        String workflowId = "user-get-" + userId + "-" + UUID.randomUUID();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(5))
                .build();

        UserGetWorkflow workflow = workflowClient.newWorkflowStub(UserGetWorkflow.class, options);

        WorkflowResult<User> result = workflow.getUser(userId);

        if (!result.isSuccess()) {
            throw ApplicationFailureHandler.mapErrorCode(result.errorCode(), userId);
        }

        User user = result.value();
        UserResponse response = new UserResponse();
        response.setUserId(user.getId().toString());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setStatus("User retrieved successfully");
        response.setWorkflowId(workflowId);

        log.info("User retrieved successfully: {} (username: {})", userId, user.getUsername());
        return response;
    }

    public void deleteUser(String userId) {
        log.info("Deleting user: {}", userId);

        String workflowId = "user-delete-" + userId + "-" + UUID.randomUUID();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(5))
                .build();

        UserDeleteWorkflow workflow = workflowClient.newWorkflowStub(UserDeleteWorkflow.class, options);

        WorkflowResult<String> result = workflow.deleteUser(userId);

        if (!result.isSuccess()) {
            throw ApplicationFailureHandler.mapErrorCode(result.errorCode(), userId);
        }

        log.info("User deleted successfully: {}", userId);
    }
}
