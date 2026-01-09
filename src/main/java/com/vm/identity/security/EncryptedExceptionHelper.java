package com.vm.identity.security;

import io.temporal.api.common.v1.Payloads;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.Values;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Helper utility for creating ApplicationFailure exceptions with encrypted details.
 *
 * This ensures that sensitive error information (HTTP error messages, stack traces, etc.)
 * is encrypted before being stored in Temporal's workflow history.
 *
 * Usage:
 * <pre>
 * throw EncryptedExceptionHelper.newFailure(
 *     dataConverter,
 *     "Failed to update search index",
 *     "SearchIndexUpdateFailed",
 *     Map.of(
 *         "method", "PATCH",
 *         "endpoint", "/search/index",
 *         "error", errorMessage
 *     )
 * );
 * </pre>
 */
public class EncryptedExceptionHelper {

    private static final Logger log = LoggerFactory.getLogger(EncryptedExceptionHelper.class);

    /**
     * Create an ApplicationFailure with encrypted details.
     *
     * @param dataConverter The Temporal data converter (includes encryption codec)
     * @param message User-visible error message
     * @param type Error type identifier (e.g., "SearchIndexUpdateFailed")
     * @param details Map of additional details to be encrypted
     * @return ApplicationFailure with encrypted details
     */
    public static ApplicationFailure newFailure(
            DataConverter dataConverter,
            String message,
            String type,
            Map<String, Object> details) {
        return newFailure(dataConverter, message, type, details, null, false);
    }

    /**
     * Create an ApplicationFailure with encrypted details and a cause.
     *
     * @param dataConverter The Temporal data converter (includes encryption codec)
     * @param message User-visible error message
     * @param type Error type identifier
     * @param details Map of additional details to be encrypted
     * @param cause Original exception (optional)
     * @return ApplicationFailure with encrypted details
     */
    public static ApplicationFailure newFailure(
            DataConverter dataConverter,
            String message,
            String type,
            Map<String, Object> details,
            Throwable cause) {
        return newFailure(dataConverter, message, type, details, cause, false);
    }

    /**
     * Create a non-retryable ApplicationFailure with encrypted details.
     *
     * @param dataConverter The Temporal data converter (includes encryption codec)
     * @param message User-visible error message
     * @param type Error type identifier
     * @param details Map of additional details to be encrypted
     * @param nonRetryable Whether the failure should be retried
     * @return ApplicationFailure with encrypted details
     */
    public static ApplicationFailure newNonRetryableFailure(
            DataConverter dataConverter,
            String message,
            String type,
            Map<String, Object> details) {
        return newFailure(dataConverter, message, type, details, null, true);
    }

    /**
     * Create an ApplicationFailure with encrypted details (full control).
     *
     * @param dataConverter The Temporal data converter (includes encryption codec)
     * @param message User-visible error message
     * @param type Error type identifier
     * @param details Map of additional details to be encrypted
     * @param cause Original exception (optional)
     * @param nonRetryable Whether the failure should be retried
     * @return ApplicationFailure with encrypted details
     */
    public static ApplicationFailure newFailure(
            DataConverter dataConverter,
            String message,
            String type,
            Map<String, Object> details,
            Throwable cause,
            boolean nonRetryable) {

        try {
            // Convert details map to Temporal Payloads (will be encrypted by codec)
            Optional<Payloads> encryptedPayloads = dataConverter.toPayloads(details);

            // Create ApplicationFailure with encrypted details
            ApplicationFailure.Builder builder = ApplicationFailure.newBuilder()
                    .setMessage(message)
                    .setType(type);

            encryptedPayloads.ifPresent(builder::setDetails);

            if (cause != null) {
                builder.setCause(cause);
            }

            builder.setNonRetryable(nonRetryable);

            log.debug("Created ApplicationFailure with encrypted details: type={}, message={}", type, message);

            return builder.build();

        } catch (Exception e) {
            // Fallback: if encryption fails, create a failure without details
            log.error("Failed to create encrypted ApplicationFailure, creating without details", e);
            return ApplicationFailure.newFailure(
                message + " (details encryption failed)",
                type,
                cause
            );
        }
    }

    /**
     * Extract and decrypt details from an ApplicationFailure.
     *
     * @param dataConverter The Temporal data converter (includes encryption codec)
     * @param failure The ApplicationFailure to extract details from
     * @param detailsClass The expected class type for details
     * @param <T> The type of details
     * @return Decrypted details, or empty Optional if not present
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getDetails(
            DataConverter dataConverter,
            ApplicationFailure failure,
            Class<T> detailsClass) {

        try {
            Values details = failure.getDetails();
            if (details == null || details.getSize() == 0) {
                return Optional.empty();
            }

            return Optional.ofNullable(details.get(0, detailsClass));

        } catch (Exception e) {
            log.error("Failed to decrypt ApplicationFailure details", e);
            return Optional.empty();
        }
    }

    /**
     * Create a simple failure for common HTTP errors.
     *
     * @param dataConverter The Temporal data converter
     * @param method HTTP method (GET, POST, etc.)
     * @param endpoint API endpoint
     * @param statusCode HTTP status code
     * @param errorMessage Error message from server
     * @return ApplicationFailure with encrypted HTTP error details
     */
    public static ApplicationFailure newHttpFailure(
            DataConverter dataConverter,
            String method,
            String endpoint,
            int statusCode,
            String errorMessage) {

        Map<String, Object> details = new HashMap<>();
        details.put("method", method);
        details.put("endpoint", endpoint);
        details.put("statusCode", statusCode);
        details.put("error", errorMessage);

        String message = String.format("%s request to %s failed: HTTP %d", method, endpoint, statusCode);
        String type = "HttpRequestFailed";

        return newFailure(dataConverter, message, type, details);
    }
}
