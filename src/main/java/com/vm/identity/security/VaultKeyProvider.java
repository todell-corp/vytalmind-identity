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

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Vault-based key provider for production environments.
 * Retrieves encryption keys from HashiCorp Vault KV v2 secrets engine on
 * application startup
 * and caches them in memory.
 *
 * Expected Vault secret structure at configured path:
 * {
 * "current-key-id": "key-2025-12",
 * "keys": {
 * "key-2025-12": "wK8RvQKJ7xLZ3pN5mF2rT9dH6yU4bV1cX0sA8iE7jG4=",
 * "key-2024-12": "oldKeyBase64Here"
 * }
 * }
 */
@Component
@ConditionalOnProperty(name = "temporal.encryption.key-provider", havingValue = "vault")
@ConfigurationProperties(prefix = "temporal.encryption.vault")
public class VaultKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultKeyProvider.class);
    private static final String ALGORITHM = "AES";
    private static final int REQUIRED_KEY_LENGTH = 32; // 256 bits

    private String uri;
    private String token;
    private String secretPath;

    private String currentKeyId;
    private final Map<String, SecretKey> cachedKeys = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Initialize by retrieving keys from Vault on application startup.
     * Called by Spring after all @ConfigurationProperties are bound.
     */
    @PostConstruct
    public void init() {
        log.info("=== VaultKeyProvider Initialization ===");
        log.info("Vault URI: {}", uri);
        log.info("Secret path: {}", secretPath);
        log.info("Token configured: {}", (token != null && !token.isEmpty()));

        if (uri == null || uri.isEmpty()) {
            log.error("Vault URI is null or empty");
            throw new KeyProviderException("Vault URI must be configured");
        }

        if (token == null || token.isEmpty()) {
            log.error("Vault token is null or empty");
            throw new KeyProviderException("Vault token must be configured");
        }

        if (secretPath == null || secretPath.isEmpty()) {
            log.error("Vault secret path is null or empty");
            throw new KeyProviderException("Vault secret path must be configured");
        }

        try {
            retrieveKeysFromVault();
            log.info("Successfully initialized VaultKeyProvider");
            log.info("Available key IDs: {}", cachedKeys.keySet());
            log.info("Current encryption key: {}", currentKeyId);
        } catch (Exception e) {
            log.error("Failed to retrieve encryption keys from Vault", e);
            throw new KeyProviderException("Failed to retrieve encryption keys from Vault", e);
        }
    }

    /**
     * Retrieve encryption keys from Vault KV v2 secrets engine.
     */
    private void retrieveKeysFromVault() {
        String vaultUrl = uri + "/v1/" + secretPath;

        log.info("Retrieving encryption keys from Vault URL: {}", vaultUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Sending GET request to Vault...");
            ResponseEntity<String> response = restTemplate.exchange(
                    vaultUrl,
                    HttpMethod.GET,
                    entity,
                    String.class);

            log.info("Vault response status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Vault request failed with status: {}", response.getStatusCode());
                throw new KeyProviderException("Failed to retrieve keys from Vault: " + response.getStatusCode());
            }

            log.debug("Vault response body length: {} characters",
                    response.getBody() != null ? response.getBody().length() : 0);

            parseVaultResponse(response.getBody());
        } catch (Exception e) {
            log.error("Exception during Vault request to {}: {}", vaultUrl, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Parse Vault KV v2 response and extract encryption keys.
     */
    private void parseVaultResponse(String responseBody) {
        try {
            log.debug("Parsing Vault response...");
            JsonNode root = objectMapper.readTree(responseBody);

            log.debug("Vault response root fields: {}", root.fieldNames());
            JsonNode data = root.path("data").path("data");

            if (data.isMissingNode()) {
                log.error("Invalid Vault response: missing data.data field");
                log.debug("Full Vault response: {}", responseBody);
                throw new KeyProviderException("Invalid Vault response: missing data.data field");
            }

            log.debug("Vault data.data fields: {}", data.fieldNames());

            // Extract current-key-id
            JsonNode currentKeyIdNode = data.path("current-key-id");
            if (currentKeyIdNode.isMissingNode()) {
                log.error("Invalid Vault response: missing current-key-id field");
                log.debug("Available fields in data.data: {}", data.fieldNames());
                throw new KeyProviderException("Invalid Vault response: missing current-key-id");
            }
            this.currentKeyId = currentKeyIdNode.asText();
            log.info("Extracted current-key-id from Vault: {}", this.currentKeyId);

            // Extract keys
            JsonNode keysNode = data.path("keys");
            if (keysNode.isMissingNode() || !keysNode.isObject()) {
                log.error("Invalid Vault response: missing or invalid keys object");
                log.debug("keys node missing: {}, is object: {}",
                        keysNode.isMissingNode(),
                        keysNode.isObject());
                throw new KeyProviderException("Invalid Vault response: missing or invalid keys object");
            }

            log.info("Processing {} keys from Vault...", keysNode.size());
            keysNode.fields().forEachRemaining(entry -> {
                String keyId = entry.getKey();
                String base64Key = entry.getValue().asText();

                try {
                    SecretKey secretKey = decodeBase64Key(base64Key);
                    cachedKeys.put(keyId, secretKey);
                    log.info("Successfully loaded encryption key from Vault: {}", keyId);
                } catch (Exception e) {
                    log.error("Failed to decode key from Vault: {}, error: {}", keyId, e.getMessage(), e);
                    throw new KeyProviderException("Failed to decode key: " + keyId, e);
                }
            });

            // Verify current key exists
            if (!cachedKeys.containsKey(currentKeyId)) {
                log.error("Current key ID '{}' not found in cached keys: {}",
                        currentKeyId, cachedKeys.keySet());
                throw new KeyProviderException("Current key ID '" + currentKeyId + "' not found in Vault keys");
            }

            log.info("Successfully retrieved {} encryption keys from Vault", cachedKeys.size());

        } catch (Exception e) {
            log.error("Failed to parse Vault response: {}", e.getMessage(), e);
            throw new KeyProviderException("Failed to parse Vault response", e);
        }
    }

    @Override
    public EncryptionKey getCurrentKey() {
        log.debug("getCurrentKey() called - currentKeyId: {}, cached keys: {}",
                currentKeyId, cachedKeys.keySet());

        if (currentKeyId == null) {
            log.error("currentKeyId is null! VaultKeyProvider may not have been initialized properly.");
            log.error("Cached keys available: {}", cachedKeys.keySet());
            throw new KeyProviderException("Current key ID is null - VaultKeyProvider not initialized");
        }

        SecretKey key = cachedKeys.get(currentKeyId);
        if (key == null) {
            log.error("Current encryption key not found in cache: {}", currentKeyId);
            log.error("Available cached keys: {}", cachedKeys.keySet());
            throw new KeyProviderException("Current encryption key not found in cache: " + currentKeyId);
        }

        log.debug("Successfully retrieved current key: {}", currentKeyId);
        return new EncryptionKey(currentKeyId, key);
    }

    @Override
    public Optional<EncryptionKey> getKeyById(String keyId) {
        SecretKey key = cachedKeys.get(keyId);
        return Optional.ofNullable(key)
                .map(k -> new EncryptionKey(keyId, k));
    }

    @Override
    public boolean keyExists(String keyId) {
        return cachedKeys.containsKey(keyId);
    }

    @Override
    public String getCurrentKeyId() {
        return currentKeyId;
    }

    /**
     * Decode a Base64-encoded AES-256 key.
     */
    private SecretKey decodeBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        byte[] decodedKey = Base64.getDecoder().decode(base64Key);

        if (decodedKey.length != REQUIRED_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("AES-256 requires a 32-byte key, but got %d bytes", decodedKey.length));
        }

        return new SecretKeySpec(decodedKey, ALGORITHM);
    }

    // Spring Configuration Properties setters
    public void setUri(String uri) {
        log.debug("VaultKeyProvider.setUri() called with: {}", uri);
        this.uri = uri;
    }

    public void setToken(String token) {
        log.debug("VaultKeyProvider.setToken() called with token: {}", (token != null && !token.isEmpty()));
        this.token = token;
    }

    public void setSecretPath(String secretPath) {
        log.debug("VaultKeyProvider.setSecretPath() called with: {}", secretPath);
        this.secretPath = secretPath;
    }
}
