package com.vm.identity.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Vault-based Keycloak credential provider for production environments.
 * Retrieves Keycloak credentials from HashiCorp Vault KV v2 secrets engine on
 * application startup and caches them in memory.
 *
 * Expected Vault secret structure at configured path:
 * {
 *   "server-url": "https://keycloak.odell.com",
 *   "realm": "vytalmind",
 *   "client-id": "vytalmind-identity-service",
 *   "client-secret": "service_account_client_secret_here"
 * }
 */
@Component
@ConditionalOnProperty(name = "keycloak.credential-provider", havingValue = "vault")
@ConfigurationProperties(prefix = "keycloak.vault")
public class VaultKeycloakCredentialProvider implements KeycloakCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultKeycloakCredentialProvider.class);

    private String uri;
    private String token;
    private String secretPath;

    private KeycloakCredentials cachedCredentials;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("=== VaultKeycloakCredentialProvider Initialization ===");
        log.info("Vault URI: {}", uri);
        log.info("Secret path: {}", secretPath);
        log.info("Token configured: {}", (token != null && !token.isEmpty()));

        if (uri == null || uri.isEmpty()) {
            throw new IllegalStateException("Vault URI must be configured");
        }

        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Vault token must be configured");
        }

        if (secretPath == null || secretPath.isEmpty()) {
            throw new IllegalStateException("Vault secret path must be configured");
        }

        try {
            retrieveCredentialsFromVault();
            log.info("Successfully initialized VaultKeycloakCredentialProvider");
        } catch (Exception e) {
            log.error("Failed to retrieve Keycloak credentials from Vault", e);
            throw new IllegalStateException("Failed to retrieve Keycloak credentials from Vault", e);
        }
    }

    private void retrieveCredentialsFromVault() {
        String vaultUrl = uri + "/v1/" + secretPath;
        log.info("Retrieving Keycloak credentials from Vault URL: {}", vaultUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    vaultUrl,
                    HttpMethod.GET,
                    entity,
                    String.class);

            log.info("Vault response status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Failed to retrieve credentials from Vault: " + response.getStatusCode());
            }

            parseVaultResponse(response.getBody());
        } catch (Exception e) {
            log.error("Exception during Vault request to {}: {}", vaultUrl, e.getMessage(), e);
            throw e;
        }
    }

    private void parseVaultResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data").path("data");

            if (data.isMissingNode()) {
                log.error("Invalid Vault response: missing data.data field");
                throw new IllegalStateException("Invalid Vault response: missing data.data field");
            }

            String serverUrl = data.path("server-url").asText();
            String realm = data.path("realm").asText();
            String clientId = data.path("client-id").asText();
            String clientSecret = data.path("client-secret").asText();

            if (serverUrl == null || serverUrl.isEmpty()) {
                throw new IllegalStateException("Keycloak server URL not found in Vault response");
            }

            if (realm == null || realm.isEmpty()) {
                throw new IllegalStateException("Keycloak realm not found in Vault response");
            }

            if (clientId == null || clientId.isEmpty()) {
                throw new IllegalStateException("Keycloak client ID not found in Vault response");
            }

            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new IllegalStateException("Keycloak client secret not found in Vault response");
            }

            cachedCredentials = new KeycloakCredentials(serverUrl, realm, clientId, clientSecret);
            log.info("Successfully retrieved Keycloak credentials from Vault");
            log.info("Keycloak Server URL: {}", serverUrl);
            log.info("Keycloak Realm: {}", realm);
            log.info("Keycloak Client ID: {}", clientId);

        } catch (Exception e) {
            log.error("Failed to parse Vault response", e);
            throw new IllegalStateException("Failed to parse Vault response", e);
        }
    }

    @Override
    public KeycloakCredentials getCredentials() {
        if (cachedCredentials == null) {
            throw new IllegalStateException("Keycloak credentials not initialized");
        }
        return cachedCredentials;
    }

    // Spring Configuration Properties setters
    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setSecretPath(String secretPath) {
        this.secretPath = secretPath;
    }
}
