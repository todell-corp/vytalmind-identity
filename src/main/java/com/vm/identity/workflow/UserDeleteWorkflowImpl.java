package com.vm.identity.workflow;

import com.vm.identity.activity.UserDatabaseActivity;
import com.vm.identity.activity.UserKeycloakActivity;
import com.vm.identity.entity.User;
import com.vm.identity.entity.UserProfile;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
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
    public String deleteUser(String userId) {
        log.info("Starting user deletion workflow for userId: {}", userId);

        Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());

        try {
            UUID userUuid = UUID.fromString(userId);

            // Step 1: Get user data for Keycloak deletion
            log.info("Fetching user data for userId: {}", userId);
            User user = databaseActivity.getUser(userUuid);
            String keycloakUserId = user.getIdpId();

            // Step 2: Delete user from Keycloak first
            log.info("Deleting user from Keycloak for userId: {}", userId);
            keycloakActivity.delete(keycloakUserId);
            saga.addCompensation(() -> {
                log.warn("Compensating: Cannot fully restore Keycloak user for userId: {} (password lost)", userId);
                // Note: Keycloak user can be partially restored but password cannot be recovered
            });

            // Step 3: Soft delete user from database
            log.info("Soft deleting user from database for userId: {}", userId);
            databaseActivity.deleteUser(userUuid);
            saga.addCompensation(() -> {
                log.warn("Compensating: Restoring user in database for userId: {}", userId);
                // Restore user by unsetting the deleted flag
                User restoredUser = databaseActivity.getUser(userUuid);
                restoredUser.setDeleted(false);
                restoredUser.setDeletedAt(null);
                databaseActivity.updateUser(userUuid, restoredUser);
            });

            log.info("User deletion workflow completed successfully for userId: {}", userId);
            return "User deleted successfully";

        } catch (Exception e) {
            log.error("Error in user deletion workflow for userId: {}. Executing compensations.", userId, e);
            saga.compensate();
            throw e;
        }
    }
}
