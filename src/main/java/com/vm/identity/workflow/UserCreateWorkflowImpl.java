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
import java.util.List;
import java.util.HashMap;

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
    public WorkflowResult<String> createUser(String userId, String email, String password,
            String firstName,
            String lastName) {
        log.info("Starting user creation workflow for userId: {}, email: {}", userId, email);

        // Step 0: Validate inputs before creating saga (validation errors don't need
        // compensation)
        UUID userUuid = UUID.fromString(userId);

        log.info("Checking if email already exists: email={}", email);
        boolean userExists = databaseActivity.checkEmailExists(email);
        if (userExists) {
            log.error("Email already exists: email={}", email);
            return WorkflowResult.error("UserAlreadyExists",
                    Map.of("email", email));
        }

        // Create saga after validation passes
        Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());

        try {
            // Step 1: Create user in Keycloak
            log.info("Creating user in Keycloak for email: {}", email);
            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setUsername(email);
            keycloakUser.setEmail(email);
            keycloakUser.setFirstName(firstName);
            keycloakUser.setLastName(lastName);
            keycloakUser.setEnabled(false);

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
            log.info("Creating user in database for userId: {}, email: {}", userId, email);
            User user = new User();
            user.setId(userUuid);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setIdpId(keycloakUserId);

            User createdUser = databaseActivity.createUser(user);
            saga.addCompensation(() -> {
                log.warn("Compensating: Deleting user from database: {}", userId);
                databaseActivity.deleteUser(userUuid);
            });

            // Step 3: Update Keycloak user with vytalmind_user_id attribute (keep disabled)
            log.info("Updating Keycloak user with vytalmind_user_id attribute: {}", keycloakUserId);

            UserRepresentation updateKeycloakUser = keycloakActivity.get(keycloakUserId);

            // Set vytalmind_user_id attribute using the full attributes map
            Map<String, List<String>> attributes = updateKeycloakUser.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }

            log.info("Postgres user: {}", createdUser.getId().toString());
            attributes.put("vytalmind_user_id", Collections.singletonList(createdUser.getId().toString()));
            updateKeycloakUser.setAttributes(attributes);
            // NOTE: Do NOT set enabled=true here - user stays disabled until role assigned

            keycloakActivity.update(keycloakUserId, updateKeycloakUser);

            // Step 4: Assign default client role to user (while still disabled)
            log.info("Assigning default client role to user in Keycloak: {}", keycloakUserId);

            String clientId = "vytalmind-api";
            String roleName = "user";

            saga.addCompensation(() -> {
                log.warn("Compensating: Removing client role '{}' from client '{}' from user: {}",
                        roleName, clientId, keycloakUserId);
                keycloakActivity.removeClientRole(keycloakUserId, clientId, roleName);
            });

            keycloakActivity.assignClientRole(keycloakUserId, clientId, roleName);

            // Step 5: Enable user account (final step after attributes + role assigned)
            log.info("Enabling Keycloak user account: {}", keycloakUserId);

            saga.addCompensation(() -> {
                log.warn("Compensating: Disabling user in Keycloak: {}", keycloakUserId);
                keycloakActivity.disable(keycloakUserId);
            });

            UserRepresentation enableUser = keycloakActivity.get(keycloakUserId);
            enableUser.setEnabled(true);
            keycloakActivity.update(keycloakUserId, enableUser);

            log.info("User creation workflow completed successfully for userId: {}, email: {}", userId, email);
            return WorkflowResult.ok(createdUser.getId().toString());

        } catch (ApplicationFailure e) {
            log.error(
                    "Application failure in user creation workflow for userId: {}, email: {}. Executing compensations.",
                    userId,
                    email, e);
            saga.compensate();
            return WorkflowResult.error(e.getType());
        } catch (Exception e) {
            log.error("Error in user creation workflow for userId: {}, email: {}. Executing compensations.", userId,
                    email, e);
            saga.compensate();
            throw e;
        }
    }
}
