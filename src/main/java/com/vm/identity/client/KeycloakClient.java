package com.vm.identity.client;

import com.vm.identity.exception.ServiceException;
import com.vm.identity.security.KeycloakCredentialProvider;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Client for interacting with Keycloak Admin API.
 * Uses OAuth2 Client Credentials flow with service account.
 */
@Component
public class KeycloakClient {
    private static final Logger log = LoggerFactory.getLogger(KeycloakClient.class);

    private final KeycloakCredentialProvider credentialProvider;
    private final String apiClientId;
    private final String defaultUserRole;
    private Keycloak keycloak;
    private String realm;
    private String apiClientUuid;

    public KeycloakClient(
            KeycloakCredentialProvider credentialProvider,
            @Value("${keycloak.api-client-id}") String apiClientId,
            @Value("${keycloak.default-user-role}") String defaultUserRole) {
        this.credentialProvider = credentialProvider;
        this.apiClientId = apiClientId;
        this.defaultUserRole = defaultUserRole;
    }

    @PostConstruct
    public void init() {
        KeycloakCredentialProvider.KeycloakCredentials creds = credentialProvider.getCredentials();
        this.realm = creds.realm();

        // Use client credentials (service account) instead of username/password
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(creds.serverUrl())
                .realm(creds.realm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)  // Service account flow
                .clientId(creds.clientId())
                .clientSecret(creds.clientSecret())
                .build();

        log.info("Keycloak client initialized with service account for realm: {}", creds.realm());
        log.info("Keycloak server URL: {}", creds.serverUrl());

        // Cache API client UUID for role operations
        try {
            this.apiClientUuid = getClientUuidByClientId(apiClientId);
            log.info("Cached API client UUID for client '{}': {}", apiClientId, apiClientUuid);
        } catch (Exception e) {
            log.error("Failed to retrieve client UUID for clientId: {}. Role operations will fail.", apiClientId, e);
            throw new IllegalStateException("Cannot initialize KeycloakClient: client UUID lookup failed", e);
        }
    }

    /**
     * Create a user in Keycloak.
     *
     * @param userRep the user representation
     * @return the Keycloak user ID (idpId)
     */
    public String createUser(UserRepresentation userRep) {
        log.info("Creating user in Keycloak: {}", userRep.getUsername());

        try (Response response = keycloak.realm(realm).users().create(userRep)) {
            if (response.getStatus() != 201) {
                String errorMsg = String.format("Failed to create user in Keycloak. Status: %d, Reason: %s",
                        response.getStatus(), response.getStatusInfo());
                log.error(errorMsg);
                throw new ServiceException(errorMsg, response.getStatus());
            }

            // Extract user ID from Location header
            String location = response.getHeaderString("Location");
            if (location == null || location.isEmpty()) {
                throw new ServiceException("Keycloak did not return user location", 500);
            }

            String userId = location.substring(location.lastIndexOf('/') + 1);
            log.info("Successfully created user in Keycloak with ID: {}", userId);
            return userId;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak", e);
            throw new ServiceException("Failed to create user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Get a user from Keycloak by ID.
     *
     * @param userId the Keycloak user ID
     * @return the user representation
     */
    public UserRepresentation getUserById(String userId) {
        log.info("Fetching user from Keycloak: {}", userId);

        try {
            UserRepresentation user = keycloak.realm(realm)
                    .users()
                    .get(userId)
                    .toRepresentation();

            log.info("Successfully fetched user from Keycloak: {}", userId);
            return user;
        } catch (Exception e) {
            log.error("Failed to fetch user from Keycloak: {}", userId, e);
            throw new ServiceException("Failed to fetch user from Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Update a user in Keycloak.
     *
     * @param userId  the Keycloak user ID
     * @param userRep the updated user representation
     */
    public void updateUser(String userId, UserRepresentation userRep) {
        log.info("Updating user in Keycloak: {}", userId);

        try {
            keycloak.realm(realm)
                    .users()
                    .get(userId)
                    .update(userRep);

            log.info("Successfully updated user in Keycloak: {}", userId);
        } catch (Exception e) {
            log.error("Failed to update user in Keycloak: {}", userId, e);
            throw new ServiceException("Failed to update user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a user from Keycloak.
     *
     * @param userId the Keycloak user ID
     */
    public void deleteUser(String userId) {
        log.info("Deleting user from Keycloak: {}", userId);

        try {
            Response response = keycloak.realm(realm)
                    .users()
                    .delete(userId);

            if (response != null && response.getStatus() >= 400) {
                String errorMsg = String.format("Failed to delete user from Keycloak. Status: %d, Reason: %s",
                        response.getStatus(), response.getStatusInfo());
                log.error(errorMsg);
                throw new ServiceException(errorMsg, response.getStatus());
            }

            log.info("Successfully deleted user from Keycloak: {}", userId);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete user from Keycloak: {}", userId, e);
            throw new ServiceException("Failed to delete user from Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Disable a user in Keycloak by setting enabled=false.
     *
     * @param userId the Keycloak user ID
     */
    public void disableUser(String userId) {
        log.info("Disabling user in Keycloak: {}", userId);

        try {
            UserRepresentation user = keycloak.realm(realm)
                    .users()
                    .get(userId)
                    .toRepresentation();

            user.setEnabled(false);

            keycloak.realm(realm)
                    .users()
                    .get(userId)
                    .update(user);

            log.info("Successfully disabled user in Keycloak: {}", userId);
        } catch (Exception e) {
            log.error("Failed to disable user in Keycloak: {}", userId, e);
            throw new ServiceException("Failed to disable user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Get client UUID by client-id (one-time lookup at initialization).
     *
     * @param clientId the client-id (e.g., "vytalmind-api")
     * @return the client UUID (e.g., "f8d3c9e1-...")
     */
    private String getClientUuidByClientId(String clientId) {
        log.info("Looking up client UUID for clientId: {}", clientId);

        try {
            List<ClientRepresentation> clients = keycloak.realm(realm)
                    .clients()
                    .findByClientId(clientId);

            if (clients.isEmpty()) {
                throw new ServiceException("Client not found: " + clientId, 404);
            }

            String uuid = clients.get(0).getId();
            log.info("Found client UUID: {} for clientId: {}", uuid, clientId);
            return uuid;
        } catch (Exception e) {
            log.error("Failed to lookup client UUID for clientId: {}", clientId, e);
            throw new ServiceException("Failed to lookup client UUID: " + e.getMessage(), e);
        }
    }

    /**
     * Assign a client role to a user.
     *
     * @param userId   the Keycloak user ID
     * @param clientId the client-id (e.g., "vytalmind-api")
     * @param roleName the role name (e.g., "user")
     */
    public void assignClientRole(String userId, String clientId, String roleName) {
        log.info("Assigning client role '{}' from client '{}' to user: {}", roleName, clientId, userId);

        try {
            // Get client UUID (use cached apiClientUuid if matching)
            String clientUuid;
            if (apiClientId.equals(clientId)) {
                clientUuid = this.apiClientUuid;
            } else {
                // Fallback for other clients (future extensibility)
                clientUuid = getClientUuidByClientId(clientId);
            }

            // Get role representation
            RoleRepresentation role = keycloak.realm(realm)
                    .clients()
                    .get(clientUuid)
                    .roles()
                    .get(roleName)
                    .toRepresentation();

            if (role == null) {
                throw new ServiceException("Role not found: " + roleName, 404);
            }

            // Assign role to user
            keycloak.realm(realm)
                    .users()
                    .get(userId)
                    .roles()
                    .clientLevel(clientUuid)
                    .add(Collections.singletonList(role));

            log.info("Successfully assigned client role '{}' to user: {}", roleName, userId);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to assign client role '{}' to user: {}", roleName, userId, e);
            throw new ServiceException("Failed to assign client role: " + e.getMessage(), e);
        }
    }

    /**
     * Remove a client role from a user.
     *
     * @param userId   the Keycloak user ID
     * @param clientId the client-id (e.g., "vytalmind-api")
     * @param roleName the role name (e.g., "user")
     */
    public void removeClientRole(String userId, String clientId, String roleName) {
        log.info("Removing client role '{}' from client '{}' from user: {}", roleName, clientId, userId);

        try {
            // Get client UUID (use cached apiClientUuid if matching)
            String clientUuid;
            if (apiClientId.equals(clientId)) {
                clientUuid = this.apiClientUuid;
            } else {
                clientUuid = getClientUuidByClientId(clientId);
            }

            // Get role representation
            RoleRepresentation role = keycloak.realm(realm)
                    .clients()
                    .get(clientUuid)
                    .roles()
                    .get(roleName)
                    .toRepresentation();

            if (role == null) {
                log.warn("Role '{}' not found, skipping removal (idempotent)", roleName);
                return;
            }

            // Remove role from user
            keycloak.realm(realm)
                    .users()
                    .get(userId)
                    .roles()
                    .clientLevel(clientUuid)
                    .remove(Collections.singletonList(role));

            log.info("Successfully removed client role '{}' from user: {}", roleName, userId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            // User or role not found - idempotent, log and continue
            log.warn("User or role not found during removal, continuing (idempotent): {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove client role '{}' from user: {}", roleName, userId, e);
            // Don't throw - compensation should be best-effort
            // User deletion will clean up roles anyway
        }
    }
}
