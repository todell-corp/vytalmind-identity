package com.vm.identity.workflow;

import com.vm.identity.activity.UserDatabaseActivity;
import com.vm.identity.activity.UserKeycloakActivity;
import com.vm.identity.dto.UserUpdateRequest;
import com.vm.identity.entity.User;
import com.vm.identity.exception.ApplicationFailureHandler;
import com.vm.identity.util.DeepCopyUtil;
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
import java.util.Optional;
import java.util.UUID;

public class UserUpdateWorkflowImpl implements UserUpdateWorkflow {

    private static final Logger log = Workflow.getLogger(UserUpdateWorkflowImpl.class);

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
    public WorkflowResult<User> updateUser(String userId, UserUpdateRequest request) {
        log.info("Starting user update workflow for userId: {}", userId);

        Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());

        try {
            UUID userUuid = UUID.fromString(userId);

            // Step 1: Get current user data for rollback
            log.info("Reading current user data for userId: {}", userId);
            Optional<User> currentUserOpt = databaseActivity.getUser(userUuid);
            if (currentUserOpt.isEmpty()) {
                log.error("User not found: {}", userId);
                return WorkflowResult.error("UserNotFound");
            }
            User currentUser = currentUserOpt.get();
            String keycloakUserId = currentUser.getIdpId();

            // Step 2: Check if email already exists when email is actually changing
            if (request.getEmail() != null && !request.getEmail().equals(currentUser.getEmail())) {
                String emailToCheck = request.getEmail();

                boolean emailExists = databaseActivity.checkEmailExists(emailToCheck);
                if (emailExists) {
                    log.error("Email already exists: email={}, current email: {}", emailToCheck,
                            currentUser.getEmail());
                    return WorkflowResult.error("UserAlreadyExists",
                            Map.of("email", emailToCheck));
                }
            }

            // Step 3: Update user in database if email, firstName, or lastName changed
            if (request.getEmail() != null || request.getFirstName() != null || request.getLastName() != null) {
                log.info("Updating user in database for userId: {}", userId);

                // Create a deep copy for rollback
                User updatedUser = DeepCopyUtil.deepCopy(currentUser, User.class);

                // Apply updates to user
                if (request.getEmail() != null) {
                    updatedUser.setEmail(request.getEmail());
                }
                if (request.getFirstName() != null) {
                    updatedUser.setFirstName(request.getFirstName());
                }
                if (request.getLastName() != null) {
                    updatedUser.setLastName(request.getLastName());
                }

                // Persist the updated user
                databaseActivity.updateUser(userUuid, updatedUser);

                // Use rollback copy for compensation
                saga.addCompensation(() -> {
                    log.warn("Compensating: Rolling back user update for userId: {}", userId);
                    databaseActivity.updateUser(userUuid, currentUser);
                });
            }

            // Step 4: Update Keycloak user if needed
            if (request.getEmail() != null || request.getFirstName() != null || request.getLastName() != null) {
                log.info("Updating user in Keycloak for userId: {}", userId);

                // Fetch current Keycloak user
                UserRepresentation currentKeycloakUser = keycloakActivity.get(keycloakUserId);

                // Create a deep copy for rollback
                UserRepresentation updatedKeycloakUser = DeepCopyUtil.deepCopy(currentKeycloakUser,
                        UserRepresentation.class);

                // Apply updates to Keycloak user
                if (request.getEmail() != null) {
                    updatedKeycloakUser.setEmail(request.getEmail());
                }
                if (request.getFirstName() != null) {
                    updatedKeycloakUser.setFirstName(request.getFirstName());
                }
                if (request.getLastName() != null) {
                    updatedKeycloakUser.setLastName(request.getLastName());
                }

                // Persist the updated Keycloak user
                keycloakActivity.update(keycloakUserId, updatedKeycloakUser);

                // Use rollback copy for compensation
                saga.addCompensation(() -> {
                    log.warn("Compensating: Rolling back Keycloak user update for userId: {}", userId);
                    keycloakActivity.update(keycloakUserId, currentKeycloakUser);
                });
            }

            // Step 5: Update password in Keycloak if provided
            if (request.getPassword() != null) {
                log.info("Updating password in Keycloak for userId: {}", userId);
                UserRepresentation keycloakUser = new UserRepresentation();
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(request.getPassword());
                credential.setTemporary(false);
                keycloakUser.setCredentials(Collections.singletonList(credential));

                keycloakActivity.update(keycloakUserId, keycloakUser);
                // Note: Previous password cannot be restored for security reasons
            }

            // Fetch and return the updated user
            Optional<User> updatedUserOpt = databaseActivity.getUser(userUuid);
            if (updatedUserOpt.isEmpty()) {
                log.error("User not found after update: {}", userId);
                return WorkflowResult.error("UserNotFound");
            }
            User updatedUser = updatedUserOpt.get();
            log.info("User update workflow completed successfully for userId: {}, email: {}", userId,
                    updatedUser.getEmail());
            log.info("Updated user object: id={}, email={}, firstName={}, lastName={}, idpId={}",
                    updatedUser.getId(), updatedUser.getEmail(),
                    updatedUser.getFirstName(), updatedUser.getLastName(), updatedUser.getIdpId());
            return WorkflowResult.ok(updatedUser);

        } catch (ApplicationFailure e) {
            log.error("Application failure in user update workflow for userId: {}. Executing compensations.", userId,
                    e);
            saga.compensate();
            return WorkflowResult.error(e.getType());
        } catch (Exception e) {
            log.error("Error in user update workflow for userId: {}. Executing compensations.", userId, e);
            saga.compensate();
            throw e;
        }
    }
}
