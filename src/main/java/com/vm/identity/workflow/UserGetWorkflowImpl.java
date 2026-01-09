package com.vm.identity.workflow;

import com.vm.identity.activity.UserDatabaseActivity;
import com.vm.identity.entity.User;
import com.vm.identity.exception.ApplicationFailureHandler;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;

public class UserGetWorkflowImpl implements UserGetWorkflow {

    private static final Logger log = Workflow.getLogger(UserGetWorkflowImpl.class);

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setBackoffCoefficient(2.0)
                    .build())
            .build();

    private final UserDatabaseActivity databaseActivity = Workflow.newActivityStub(UserDatabaseActivity.class,
            activityOptions);

    @Override
    public WorkflowResult<User> getUser(String userId) {
        log.info("Starting user get workflow for userId: {}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);
            return databaseActivity
                    .getUser(userUuid)
                    .map(userOpt -> WorkflowResult.ok(userOpt))
                    .orElse(WorkflowResult.error("UserNotFound"));
        } catch (ApplicationFailure e) {
            log.error("Application failure in user get workflow for userId: {}", userId, e);
            return WorkflowResult.error(e.getType());
        } catch (Exception e) {
            log.error("Error in user get workflow for userId: {}", userId, e);
            throw e;
        }
    }
}
