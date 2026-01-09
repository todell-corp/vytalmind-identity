package com.vm.identity.exception;

/**
 * Exception thrown when attempting to create a user with a username that already exists.
 */
public class UsernameAlreadyExistsException extends RuntimeException {

    private final String username;

    public UsernameAlreadyExistsException(String message, String username) {
        super(message);
        this.username = username;
    }

    public UsernameAlreadyExistsException(String message, String username, Throwable cause) {
        super(message, cause);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
