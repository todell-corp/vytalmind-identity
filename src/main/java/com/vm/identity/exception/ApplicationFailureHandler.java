package com.vm.identity.exception;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Handler for Temporal ApplicationFailure exceptions.
 * Maps specific failure types to appropriate HTTP status codes.
 */
@RestControllerAdvice
public class ApplicationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationFailureHandler.class);

    @ExceptionHandler(ApplicationFailure.class)
    public ResponseEntity<ErrorResponse> handleApplicationFailure(ApplicationFailure ex) {
        Map<String, ?> details = null;
        Object detailsObj = ex.getDetails();
        if (detailsObj instanceof Map) {
            details = (Map<String, ?>) detailsObj;
        }

        // Handle username already exists from Temporal workflow
        if ("UsernameAlreadyExists".equals(ex.getType())) {
            log.warn("Username already exists: {}", ex.getMessage());

            ErrorResponse errorResponse = new ErrorResponse(
                    "USERNAME_ALREADY_EXISTS",
                    ex.getOriginalMessage(),
                    details,
                    Instant.now());

            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }

        // Generic ApplicationFailure handling
        log.error("Application failure: type={}, message={}", ex.getType(), ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                ex.getType() != null ? ex.getType() : "APPLICATION_FAILURE",
                ex.getOriginalMessage(),
                details,
                Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Standard error response structure.
     */
    public record ErrorResponse(
            String code,
            String message,
            Map<String, ?> details,
            Instant timestamp) {
    }
}
