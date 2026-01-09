package com.vm.identity.security;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Interface for encryption key management.
 * Supports multiple key providers (environment variables, Vault, AWS KMS, etc.)
 * and enables key rotation by maintaining multiple keys simultaneously.
 */
public interface KeyProvider {

    /**
     * Represents an encryption key with its identifier.
     */
    record EncryptionKey(String keyId, SecretKey key) {}

    /**
     * Get the current active encryption key for new encryptions.
     * This key will be used when encrypting new payloads.
     *
     * @return the current encryption key
     * @throws KeyProviderException if the current key cannot be retrieved
     */
    EncryptionKey getCurrentKey();

    /**
     * Get a specific encryption key by its ID.
     * Used for decryption to support key rotation (old encrypted data can still be decrypted).
     *
     * @param keyId the key identifier
     * @return the encryption key if found
     * @throws KeyProviderException if the key cannot be retrieved
     */
    Optional<EncryptionKey> getKeyById(String keyId);

    /**
     * Check if a key with the given ID exists.
     *
     * @param keyId the key identifier
     * @return true if the key exists, false otherwise
     */
    boolean keyExists(String keyId);

    /**
     * Get the current key ID.
     *
     * @return the identifier of the current active key
     */
    String getCurrentKeyId();
}
