package com.vm.identity.activity;

import com.vm.identity.client.KeycloakClient;
import com.vm.identity.exception.ServiceException;
import io.temporal.failure.ApplicationFailure;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Implementation of UserKeycloakActivity for Temporal workflows.
 * Handles Keycloak user operations.
 */
@Component
public class UserKeycloakActivityImpl implements UserKeycloakActivity {
    private static final Logger log = LoggerFactory.getLogger(UserKeycloakActivityImpl.class);

    private final KeycloakClient keycloakClient;

    public UserKeycloakActivityImpl(KeycloakClient keycloakClient) {
        this.keycloakClient = keycloakClient;
    }

    @Override
    public String create(UserRepresentation userRep) {
        log.info("Activity: Creating user in Keycloak: {}", userRep.getEmail());
        try {
            return keycloakClient.createUser(userRep);
        } catch (Exception e) {
            log.error("Activity failed: create user in Keycloak", e);
            throw ApplicationFailure.newFailure(
                    "Failed to create user in Keycloak",
                    "KeycloakCreateFailed",
                    Map.of("email", userRep.getEmail(), "error", e.getMessage())
            );
        }
    }

    @Override
    public void update(String keycloakUserId, UserRepresentation userRep) {
        log.info("Activity: Updating user in Keycloak: {}", keycloakUserId);
        try {
            keycloakClient.updateUser(keycloakUserId, userRep);
        } catch (Exception e) {
            log.error("Activity failed: update user in Keycloak: {}", keycloakUserId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to update user in Keycloak",
                    "KeycloakUpdateFailed",
                    Map.of("keycloakUserId", keycloakUserId, "error", e.getMessage())
            );
        }
    }

    @Override
    public void delete(String keycloakUserId) {
        log.info("Activity: Deleting user from Keycloak: {}", keycloakUserId);
        try {
            keycloakClient.deleteUser(keycloakUserId);
        } catch (Exception e) {
            log.error("Activity failed: delete user from Keycloak: {}", keycloakUserId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to delete user from Keycloak",
                    "KeycloakDeleteFailed",
                    Map.of("keycloakUserId", keycloakUserId, "error", e.getMessage())
            );
        }
    }

    @Override
    public void disable(String keycloakUserId) {
        log.info("Activity: Disabling user in Keycloak: {}", keycloakUserId);
        try {
            keycloakClient.disableUser(keycloakUserId);
        } catch (Exception e) {
            log.error("Activity failed: disable user in Keycloak: {}", keycloakUserId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to disable user in Keycloak",
                    "KeycloakDisableFailed",
                    Map.of("keycloakUserId", keycloakUserId, "error", e.getMessage())
            );
        }
    }

    @Override
    public UserRepresentation get(String keycloakUserId) {
        log.info("Activity: Fetching user from Keycloak: {}", keycloakUserId);
        try {
            return keycloakClient.getUserById(keycloakUserId);
        } catch (Exception e) {
            log.error("Activity failed: get user from Keycloak: {}", keycloakUserId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to get user from Keycloak",
                    "KeycloakGetFailed",
                    Map.of("keycloakUserId", keycloakUserId, "error", e.getMessage())
            );
        }
    }

    @Override
    public void assignClientRole(String keycloakUserId, String clientId, String roleName) {
        log.info("Activity: Assigning client role '{}' from client '{}' to user: {}",
                roleName, clientId, keycloakUserId);
        try {
            keycloakClient.assignClientRole(keycloakUserId, clientId, roleName);
        } catch (ServiceException e) {
            log.error("Activity failed: assign client role '{}' to user: {}", roleName, keycloakUserId, e);

            // Map specific errors
            String errorType = switch (e.getStatusCode()) {
                case 404 -> "RoleNotFound";
                case 403 -> "KeycloakPermissionDenied";
                default -> "KeycloakRoleAssignmentFailed";
            };

            throw ApplicationFailure.newFailure(
                    "Failed to assign client role in Keycloak",
                    errorType,
                    Map.of(
                            "keycloakUserId", keycloakUserId,
                            "clientId", clientId,
                            "roleName", roleName,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            log.error("Activity failed: assign client role '{}' to user: {}", roleName, keycloakUserId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to assign client role in Keycloak",
                    "KeycloakRoleAssignmentFailed",
                    Map.of(
                            "keycloakUserId", keycloakUserId,
                            "clientId", clientId,
                            "roleName", roleName,
                            "error", e.getMessage()
                    )
            );
        }
    }

    @Override
    public void removeClientRole(String keycloakUserId, String clientId, String roleName) {
        log.info("Activity: Removing client role '{}' from client '{}' from user: {}",
                roleName, clientId, keycloakUserId);
        try {
            keycloakClient.removeClientRole(keycloakUserId, clientId, roleName);
        } catch (Exception e) {
            // Compensation should be best-effort, log but don't fail
            log.error("Activity failed (non-fatal): remove client role '{}' from user: {}",
                    roleName, keycloakUserId, e);
            // Don't throw - compensations should be resilient
        }
    }
}
