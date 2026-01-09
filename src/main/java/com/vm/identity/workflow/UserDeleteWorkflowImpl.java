package com.vm.identity.workflow;

import com.vm.identity.activity.UserDatabaseActivity;
import com.vm.identity.activity.UserKeycloakActivity;
import com.vm.identity.entity.User;
import com.vm.identity.entity.UserProfile;
import com.vm.identity.exception.ApplicationFailureHandler;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserDeleteWorkflowImpl implements UserDeleteWorkflow {

    private static final Logger log = Workflow.getLogger(UserDeleteWorkflowImpl.class);

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setBackoffCoefficient(2.0)
                    .build())
            .build();

    private final UserKeycloakActivity keycloakActivity = Workflow.newActivityStub(UserKeycloakActivity.class, activityOptions);
    private final UserDatabaseActivity databaseActivity = Workflow.newActivityStub(UserDatabaseActivity.class, activityOptions);

    @Override
    public WorkflowResult<String> deleteUser(String userId) {
        log.info("Starting user deletion workflow for userId: {}", userId);

        // Step 0: Validate user exists before creating saga (validation errors don't need compensation)
        UUID userUuid = UUID.fromString(userId);

        log.info("Checking if user exists: {}", userId);
        Optional<User> userOpt;
        try {
            userOpt = databaseActivity.getUser(userUuid);
        } catch (Exception e) {
            log.error("Error fetching user: {}", userId, e);
            return WorkflowResult.error("UserNotFound");
        }

        if (userOpt.isEmpty()) {
            log.error("User not found: {}", userId);
            return WorkflowResult.error("UserNotFound");
        }

        User user = userOpt.get();
        String keycloakUserId = user.getIdpId();

        // Create saga after validation passes
        Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());

        try {
            // Step 1: Disable user in Keycloak first
            log.info("Disabling user in Keycloak for userId: {}", userId);
            keycloakActivity.disable(keycloakUserId);
            saga.addCompensation(() -> {
                log.warn("Compensating: Re-enabling Keycloak user for userId: {}", userId);
                // Restore by fetching the user and setting enabled=true
                UserRepresentation restoredUser = keycloakActivity.get(keycloakUserId);
                restoredUser.setEnabled(true);
                keycloakActivity.update(keycloakUserId, restoredUser);
            });

            // Step 2: Soft delete user from database
            log.info("Soft deleting user from database for userId: {}", userId);
            databaseActivity.deleteUser(userUuid);
            saga.addCompensation(() -> {
                log.warn("Compensating: Restoring user in database for userId: {}", userId);
                // Restore user by unsetting the deleted flag
                databaseActivity.getUser(userUuid).ifPresent(restoredUser -> {
                    restoredUser.setDeleted(false);
                    restoredUser.setDeletedAt(null);
                    databaseActivity.updateUser(userUuid, restoredUser);
                });
            });

            log.info("User deletion workflow completed successfully for userId: {}", userId);
            return WorkflowResult.ok("User deleted successfully");

        } catch (ApplicationFailure e) {
            log.error("Application failure in user deletion workflow for userId: {}. Executing compensations.", userId, e);
            saga.compensate();
            return WorkflowResult.error(e.getType());
        } catch (Exception e) {
            log.error("Error in user deletion workflow for userId: {}. Executing compensations.", userId, e);
            saga.compensate();
            throw e;
        }
    }
}
