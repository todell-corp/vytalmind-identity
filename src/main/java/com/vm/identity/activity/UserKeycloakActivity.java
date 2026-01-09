package com.vm.identity.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Temporal activity interface for Keycloak user operations.
 * Handles all Keycloak-specific user management.
 */
@ActivityInterface
public interface UserKeycloakActivity {

    @ActivityMethod(name = "CreateUserInKeycloak")
    String create(UserRepresentation userRep);

    @ActivityMethod(name = "UpdateUserInKeycloak")
    void update(String keycloakUserId, UserRepresentation userRep);

    @ActivityMethod(name = "DeleteUserFromKeycloak")
    void delete(String keycloakUserId);

    @ActivityMethod(name = "GetUserFromKeycloak")
    UserRepresentation get(String keycloakUserId);
}
