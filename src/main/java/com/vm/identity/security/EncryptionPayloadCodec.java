package com.vm.identity.security;

import io.temporal.api.common.v1.Payload;
import io.temporal.payload.codec.PayloadCodec;
import io.temporal.shaded.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload codec that encrypts/decrypts all Temporal workflow payloads using AES-256-GCM.
 *
 * Encryption Process:
 * 1. Generate random 12-byte IV (Initialization Vector)
 * 2. Encrypt payload data using AES-256-GCM
 * 3. Prepend IV to ciphertext: [IV(12 bytes)][ciphertext + auth tag]
 * 4. Add metadata indicating encryption
 *
 * Decryption Process:
 * 1. Check metadata to determine if payload is encrypted
 * 2. Extract IV from first 12 bytes
 * 3. Decrypt remaining bytes using AES-256-GCM
 * 4. Return original payload
 *
 * Features:
 * - Backward compatible: passes through unencrypted payloads
 * - Key rotation: supports multiple keys via key-id metadata
 * - Authenticated encryption: GCM provides integrity verification
 */
public class EncryptionPayloadCodec implements PayloadCodec {

    private static final Logger log = LoggerFactory.getLogger(EncryptionPayloadCodec.class);

    // Metadata keys
    private static final String METADATA_ENCODING = "encoding";
    private static final String METADATA_ENCRYPTION_CIPHER = "encryption-cipher";
    private static final String METADATA_ENCRYPTION_KEY_ID = "encryption-key-id";
    private static final String METADATA_ORIGINAL_ENCODING = "original-encoding";

    // Metadata values
    private static final String ENCODING_ENCRYPTED = "binary/encrypted";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    // GCM parameters
    private static final int GCM_IV_LENGTH = 12; // 96 bits (recommended for GCM)
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom;

    public EncryptionPayloadCodec(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
        this.secureRandom = new SecureRandom();
        log.info("EncryptionPayloadCodec initialized with key provider: {}", keyProvider.getClass().getSimpleName());
    }

    @Override
    public List<Payload> encode(List<Payload> payloads) {
        List<Payload> encodedPayloads = new ArrayList<>(payloads.size());

        for (Payload payload : payloads) {
            try {
                Payload encoded = encryptPayload(payload);
                encodedPayloads.add(encoded);
            } catch (Exception e) {
                log.error("Failed to encrypt payload", e);
                throw new RuntimeException("Encryption failed", e);
            }
        }

        return encodedPayloads;
    }

    @Override
    public List<Payload> decode(List<Payload> payloads) {
        List<Payload> decodedPayloads = new ArrayList<>(payloads.size());

        for (Payload payload : payloads) {
            try {
                Payload decoded = decryptPayload(payload);
                decodedPayloads.add(decoded);
            } catch (Exception e) {
                log.error("Failed to decrypt payload", e);
                throw new RuntimeException("Decryption failed", e);
            }
        }

        return decodedPayloads;
    }

    /**
     * Encrypt a single payload.
     */
    private Payload encryptPayload(Payload payload) throws Exception {
        // Check if already encrypted (avoid double encryption)
        if (isEncrypted(payload)) {
            log.debug("Payload already encrypted, skipping");
            return payload;
        }

        // Get current encryption key
        KeyProvider.EncryptionKey encryptionKey = keyProvider.getCurrentKey();
        SecretKey secretKey = encryptionKey.key();
        String keyId = encryptionKey.keyId();

        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Encrypt payload data
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] plaintext = payload.getData().toByteArray();
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Combine IV + ciphertext
        byte[] encryptedData = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encryptedData, 0, iv.length);
        System.arraycopy(ciphertext, 0, encryptedData, iv.length, ciphertext.length);

        // Build encrypted payload with metadata
        Payload.Builder builder = Payload.newBuilder()
                .setData(ByteString.copyFrom(encryptedData));

        // Preserve original encoding metadata under a different key
        ByteString originalEncoding = payload.getMetadataMap().get(METADATA_ENCODING);
        if (originalEncoding != null) {
            builder.putMetadata(METADATA_ORIGINAL_ENCODING, originalEncoding);
        }

        // Add encryption metadata
        builder.putMetadata(METADATA_ENCODING,
            ByteString.copyFromUtf8(ENCODING_ENCRYPTED));
        builder.putMetadata(METADATA_ENCRYPTION_CIPHER,
            ByteString.copyFromUtf8(CIPHER_ALGORITHM));
        builder.putMetadata(METADATA_ENCRYPTION_KEY_ID,
            ByteString.copyFromUtf8(keyId));

        // Preserve original metadata (except encoding which we handle separately)
        payload.getMetadataMap().forEach((key, value) -> {
            if (!key.equals(METADATA_ENCODING)) {
                builder.putMetadata(key, value);
            }
        });

        log.debug("Encrypted payload with key: {}, size: {} -> {} bytes",
            keyId, plaintext.length, encryptedData.length);

        return builder.build();
    }

    /**
     * Decrypt a single payload.
     */
    private Payload decryptPayload(Payload payload) throws Exception {
        // Check if payload is encrypted
        if (!isEncrypted(payload)) {
            log.debug("Payload not encrypted, passing through (backward compatibility)");
            return payload;
        }

        // Extract key ID from metadata
        String keyId = getKeyIdFromMetadata(payload);
        if (keyId == null) {
            throw new IllegalStateException("Encrypted payload missing key-id metadata");
        }

        // Get decryption key
        KeyProvider.EncryptionKey encryptionKey = keyProvider.getKeyById(keyId)
                .orElseThrow(() -> new KeyProviderException("Encryption key not found: " + keyId));

        SecretKey secretKey = encryptionKey.key();

        // Extract encrypted data
        byte[] encryptedData = payload.getData().toByteArray();

        if (encryptedData.length < GCM_IV_LENGTH) {
            throw new IllegalStateException("Encrypted data too short to contain IV");
        }

        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);

        byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        // Decrypt
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);

        // Build decrypted payload with original metadata (minus encryption metadata)
        Payload.Builder builder = Payload.newBuilder()
                .setData(ByteString.copyFrom(plaintext));

        // Restore original encoding metadata
        ByteString originalEncoding = payload.getMetadataMap().get(METADATA_ORIGINAL_ENCODING);
        if (originalEncoding != null) {
            builder.putMetadata(METADATA_ENCODING, originalEncoding);
        }

        // Copy all other metadata except encryption-specific keys
        payload.getMetadataMap().forEach((key, value) -> {
            if (!key.equals(METADATA_ENCODING) &&
                !key.equals(METADATA_ENCRYPTION_CIPHER) &&
                !key.equals(METADATA_ENCRYPTION_KEY_ID) &&
                !key.equals(METADATA_ORIGINAL_ENCODING)) {
                builder.putMetadata(key, value);
            }
        });

        log.debug("Decrypted payload with key: {}, size: {} -> {} bytes",
            keyId, encryptedData.length, plaintext.length);

        return builder.build();
    }

    /**
     * Check if a payload is encrypted based on metadata.
     */
    private boolean isEncrypted(Payload payload) {
        ByteString encoding = payload.getMetadataMap().get(METADATA_ENCODING);
        return encoding != null && ENCODING_ENCRYPTED.equals(encoding.toStringUtf8());
    }

    /**
     * Extract key ID from payload metadata.
     */
    private String getKeyIdFromMetadata(Payload payload) {
        ByteString keyId = payload.getMetadataMap().get(METADATA_ENCRYPTION_KEY_ID);
        return keyId != null ? keyId.toStringUtf8() : null;
    }
}
