package com.vm.identity.workflow;

import com.vm.identity.activity.UserDatabaseActivity;
import com.vm.identity.activity.UserKeycloakActivity;
import com.vm.identity.entity.User;
import com.vm.identity.exception.ApplicationFailureHandler;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class UserCreateWorkflowImpl implements UserCreateWorkflow {

    private static final Logger log = Workflow.getLogger(UserCreateWorkflowImpl.class);

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setBackoffCoefficient(2.0)
                    .build())
            .build();

    private final UserKeycloakActivity keycloakActivity = Workflow.newActivityStub(UserKeycloakActivity.class,
            activityOptions);
    private final UserDatabaseActivity databaseActivity = Workflow.newActivityStub(UserDatabaseActivity.class,
            activityOptions);

    @Override
    public WorkflowResult<String> createUser(String userId, String username, String email, String password,
            String firstName,
            String lastName) {
        log.info("Starting user creation workflow for userId: {}, username: {}", userId, username);

        // Step 0: Validate inputs before creating saga (validation errors don't need
        // compensation)
        UUID userUuid = UUID.fromString(userId);

        log.info("Checking if email or username already exists: email={}, username={}", email, username);
        boolean userExists = databaseActivity.checkEmailOrUsernameExists(email, username);
        if (userExists) {
            log.error("Email or username already exists: email={}, username={}", email, username);
            return WorkflowResult.error("UserAlreadyExists",
                    Map.of("username", username, "email", email));
        }

        // Create saga after validation passes
        Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());

        try {
            // Step 1: Create user in Keycloak
            log.info("Creating user in Keycloak for username: {}", username);
            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setUsername(username);
            keycloakUser.setEmail(email);
            keycloakUser.setFirstName(firstName);
            keycloakUser.setLastName(lastName);
            keycloakUser.setEnabled(true);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            keycloakUser.setCredentials(Collections.singletonList(credential));

            String keycloakUserId = keycloakActivity.create(keycloakUser);
            saga.addCompensation(() -> {
                log.warn("Compensating: Deleting user from Keycloak: {}", keycloakUserId);
                keycloakActivity.delete(keycloakUserId);
            });

            // Step 2: Create user in database
            log.info("Creating user in database for userId: {}, username: {}", userId, username);
            User user = new User();
            user.setId(userUuid);
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setIdpId(keycloakUserId);

            User createdUser = databaseActivity.createUser(user);
            saga.addCompensation(() -> {
                log.warn("Compensating: Deleting user from database: {}", userId);
                databaseActivity.deleteUser(userUuid);
            });

            log.info("User creation workflow completed successfully for userId: {}, username: {}", userId, username);
            return WorkflowResult.ok(createdUser.getId().toString());

        } catch (ApplicationFailure e) {
            log.error(
                    "Application failure in user creation workflow for userId: {}, username: {}. Executing compensations.",
                    userId,
                    username, e);
            saga.compensate();
            return WorkflowResult.error(e.getType());
        } catch (Exception e) {
            log.error("Error in user creation workflow for userId: {}, username: {}. Executing compensations.", userId,
                    username, e);
            saga.compensate();
            throw e;
        }
    }
}
