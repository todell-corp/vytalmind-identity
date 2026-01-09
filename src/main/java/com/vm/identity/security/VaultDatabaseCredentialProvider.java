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
 * Vault-based database credential provider for production environments.
 * Retrieves database credentials from HashiCorp Vault KV v2 secrets engine on
 * application startup and caches them in memory.
 *
 * Expected Vault secret structure at configured path:
 * {
 *   "url": "jdbc:postgresql://postgres:5432/vytalmind-identity",
 *   "username": "vytalmind_user",
 *   "password": "secure_password_here"
 * }
 */
@Component
@ConditionalOnProperty(name = "database.credential-provider", havingValue = "vault")
@ConfigurationProperties(prefix = "database.vault")
public class VaultDatabaseCredentialProvider implements DatabaseCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultDatabaseCredentialProvider.class);

    private String uri;
    private String token;
    private String secretPath;

    private DatabaseCredentials cachedCredentials;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("=== VaultDatabaseCredentialProvider Initialization ===");
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
            log.info("Successfully initialized VaultDatabaseCredentialProvider");
        } catch (Exception e) {
            log.error("Failed to retrieve database credentials from Vault", e);
            throw new IllegalStateException("Failed to retrieve database credentials from Vault", e);
        }
    }

    private void retrieveCredentialsFromVault() {
        String vaultUrl = uri + "/v1/" + secretPath;
        log.info("Retrieving database credentials from Vault URL: {}", vaultUrl);

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

            String url = data.path("url").asText();
            String username = data.path("username").asText();
            String password = data.path("password").asText();

            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("Database URL not found in Vault response");
            }

            if (username == null || username.isEmpty()) {
                throw new IllegalStateException("Database username not found in Vault response");
            }

            if (password == null || password.isEmpty()) {
                throw new IllegalStateException("Database password not found in Vault response");
            }

            cachedCredentials = new DatabaseCredentials(url, username, password);
            log.info("Successfully retrieved database credentials from Vault");
            log.info("Database URL: {}", url);
            log.info("Database Username: {}", username);

        } catch (Exception e) {
            log.error("Failed to parse Vault response", e);
            throw new IllegalStateException("Failed to parse Vault response", e);
        }
    }

    @Override
    public DatabaseCredentials getCredentials() {
        if (cachedCredentials == null) {
            throw new IllegalStateException("Database credentials not initialized");
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
