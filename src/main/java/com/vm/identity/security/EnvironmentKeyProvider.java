package com.vm.identity.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Environment-based key provider for development and staging environments.
 * Loads AES-256 encryption keys from application configuration (backed by
 * environment variables).
 *
 * Configuration example:
 * temporal:
 * encryption:
 * current-key-id: key-2025-12
 * keys:
 * key-2025-12: wK8RvQKJ7xLZ3pN5mF2rT9dH6yU4bV1cX0sA8iE7jG4=
 */
@Component
@ConditionalOnProperty(name = "temporal.encryption.key-provider", havingValue = "environment", matchIfMissing = true)
@ConfigurationProperties(prefix = "temporal.encryption")
public class EnvironmentKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentKeyProvider.class);
    private static final String ALGORITHM = "AES";
    private static final int REQUIRED_KEY_LENGTH = 32; // 256 bits

    private String currentKeyId;
    private Map<String, String> keys = new HashMap<>();
    private final Map<String, SecretKey> decodedKeys = new HashMap<>();

    /**
     * Initialize the key provider by decoding all configured keys.
     * Called by Spring after bean creation via @PostConstruct.
     */
    @PostConstruct
    public void init() {
        log.info("=== EnvironmentKeyProvider Initialization ===");
        log.info("Current key ID: {}", currentKeyId);
        log.info("Total keys configured: {}", keys != null ? keys.size() : 0);
        if (keys != null) {
            log.info("Configured key IDs: {}", keys.keySet());
        }

        if (currentKeyId == null || currentKeyId.isEmpty()) {
            throw new KeyProviderException("Current key ID must be configured");
        }

        if (keys == null || keys.isEmpty()) {
            throw new KeyProviderException("No encryption keys configured");
        }

        // Decode and validate all keys (skip empty ones from property binding)
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            String keyId = entry.getKey();
            String base64Key = entry.getValue();

            // Skip empty values that may come from property defaults
            if (base64Key == null || base64Key.trim().isEmpty()) {
                log.warn("Skipping empty encryption key for key-id: {} (likely not needed for current provider)", keyId);
                continue;
            }

            try {
                SecretKey secretKey = decodeBase64Key(base64Key);
                decodedKeys.put(keyId, secretKey);
                log.debug("Successfully loaded encryption key: {}", keyId);
            } catch (Exception e) {
                throw new KeyProviderException("Failed to decode encryption key: " + keyId, e);
            }
        }

        // Verify current key is available (only if we loaded any keys)
        if (!decodedKeys.isEmpty() && !decodedKeys.containsKey(currentKeyId)) {
            throw new KeyProviderException(
                    "Current encryption key not found. Key ID: " + currentKeyId +
                    ", Available keys: " + decodedKeys.keySet()
            );
        }

        log.info("Successfully initialized EnvironmentKeyProvider");
        log.info("Available key IDs: {}", decodedKeys.keySet());
        log.info("Current encryption key: {}", currentKeyId);
    }

    @Override
    public EncryptionKey getCurrentKey() {
        SecretKey key = decodedKeys.get(currentKeyId);
        if (key == null) {
            throw new KeyProviderException("Current encryption key not found: " + currentKeyId);
        }
        return new EncryptionKey(currentKeyId, key);
    }

    @Override
    public Optional<EncryptionKey> getKeyById(String keyId) {
        SecretKey key = decodedKeys.get(keyId);
        return Optional.ofNullable(key)
                .map(k -> new EncryptionKey(keyId, k));
    }

    @Override
    public boolean keyExists(String keyId) {
        return decodedKeys.containsKey(keyId);
    }

    @Override
    public String getCurrentKeyId() {
        return currentKeyId;
    }

    /**
     * Decode a Base64-encoded AES-256 key.
     *
     * @param base64Key the Base64-encoded key
     * @return the decoded SecretKey
     * @throws IllegalArgumentException if the key is invalid
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
    public void setCurrentKeyId(String currentKeyId) {
        this.currentKeyId = currentKeyId;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
        // Validation deferred to @PostConstruct
    }
}
