package com.vm.identity.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Environment-based Keycloak credential provider for development and staging environments.
 * Loads Keycloak credentials from application configuration (backed by environment variables).
 */
@Component
@ConditionalOnProperty(name = "keycloak.credential-provider", havingValue = "environment", matchIfMissing = true)
@ConfigurationProperties(prefix = "keycloak")
public class EnvironmentKeycloakCredentialProvider implements KeycloakCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentKeycloakCredentialProvider.class);

    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;

    @PostConstruct
    public void init() {
        log.info("=== EnvironmentKeycloakCredentialProvider Initialization ===");
        log.info("Keycloak Server URL: {}", serverUrl);
        log.info("Keycloak Realm: {}", realm);
        log.info("Keycloak Client ID: {}", clientId);

        if (serverUrl == null || serverUrl.isEmpty()) {
            throw new IllegalStateException("Keycloak server URL must be configured");
        }

        if (realm == null || realm.isEmpty()) {
            throw new IllegalStateException("Keycloak realm must be configured");
        }

        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("Keycloak client ID must be configured");
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("Keycloak client secret must be configured");
        }

        log.info("Successfully initialized EnvironmentKeycloakCredentialProvider");
    }

    @Override
    public KeycloakCredentials getCredentials() {
        return new KeycloakCredentials(serverUrl, realm, clientId, clientSecret);
    }

    // Spring Configuration Properties setters
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
