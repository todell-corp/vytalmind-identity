package com.vm.identity.activity;

import com.vm.identity.client.KeycloakClient;
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
        log.info("Activity: Creating user in Keycloak: {}", userRep.getUsername());
        try {
            return keycloakClient.createUser(userRep);
        } catch (Exception e) {
            log.error("Activity failed: create user in Keycloak", e);
            throw ApplicationFailure.newFailure(
                    "Failed to create user in Keycloak",
                    "KeycloakCreateFailed",
                    Map.of("username", userRep.getUsername(), "error", e.getMessage())
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
}
