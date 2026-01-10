package com.vm.identity.exception;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mapper for Temporal ApplicationFailure exceptions.
 * Maps specific failure types to appropriate custom exceptions.
 */
@Component
public class ApplicationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationFailureHandler.class);

    /**
     * Maps a Temporal ApplicationFailure to an appropriate custom exception.
     *
     * @param ex the ApplicationFailure from Temporal workflow
     * @return RuntimeException (specific subtype based on failure type)
     */
    public static RuntimeException map(ApplicationFailure ex) {
        String failureType = ex.getType();
        String message = ex.getOriginalMessage();

        Map<String, ?> details = null;
        Object detailsObj = ex.getDetails();
        if (detailsObj instanceof Map) {
            details = (Map<String, ?>) detailsObj;
        }

        // Handle user already exists from Temporal workflow
        if ("UserAlreadyExists".equals(failureType)) {
            log.warn("User already exists: {}", message);
            String email = details != null ? (String) details.get("email") : null;
            return new UserAlreadyExistsException(message, email, ex);
        }

        // Handle user not found from Temporal workflow
        if ("UserNotFound".equals(failureType)) {
            log.warn("User not found: {}", message);
            String userId = details != null ? (String) details.get("userId") : null;
            return new UserNotFoundException(message, userId, ex);
        }

        // Generic ApplicationFailure handling
        log.error("Application failure: type={}, message={}", failureType, message, ex);
        return new RuntimeException(message != null ? message : "Application failure occurred", ex);
    }

    /**
     * Maps an error code from WorkflowResult to an appropriate custom exception.
     *
     * @param errorCode the error code from the workflow
     * @param context   additional context (e.g., username, userId)
     * @return RuntimeException (specific subtype based on error code)
     */
    public static RuntimeException mapErrorCode(String errorCode, String context) {
        return mapErrorCode(errorCode, Map.of("context", context));
    }

    /**
     * Maps an error code from WorkflowResult to an appropriate custom exception with additional details.
     *
     * @param errorCode the error code from the workflow
     * @param details   map of additional details (e.g., username, email, userId)
     * @return RuntimeException (specific subtype based on error code)
     */
    public static RuntimeException mapErrorCode(String errorCode, Map<String, String> details) {
        log.info("Mapping error code: {}, details: {}", errorCode, details);

        // Handle user already exists (email conflict)
        if ("UserAlreadyExists".equals(errorCode)) {
            String email = details.get("email");
            log.warn("User already exists - email: {}", email);
            return new UserAlreadyExistsException(
                    "User with this email already exists",
                    email
            );
        }

        // Handle user not found
        if ("UserNotFound".equals(errorCode)) {
            String context = details.get("context");
            log.warn("User not found: {}", context);
            return new UserNotFoundException("User not found: " + context, context);
        }

        // Generic error handling
        log.error("Application failure: errorCode={}, details={}", errorCode, details);
        return new RuntimeException("Application failure: " + errorCode);
    }
}
