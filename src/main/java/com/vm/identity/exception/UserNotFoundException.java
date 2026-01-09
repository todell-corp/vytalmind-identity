package com.vm.identity.exception;

/**
 * Exception thrown when a user is not found.
 */
public class UserNotFoundException extends RuntimeException {

    private final String userId;

    public UserNotFoundException(String message, String userId) {
        super(message);
        this.userId = userId;
    }

    public UserNotFoundException(String message, String userId, Throwable cause) {
        super(message, cause);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
