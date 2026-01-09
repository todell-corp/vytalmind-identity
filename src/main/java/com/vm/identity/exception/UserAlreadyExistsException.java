package com.vm.identity.exception;

/**
 * Exception thrown when attempting to create or update a user with an email or username that already exists.
 */
public class UserAlreadyExistsException extends RuntimeException {

    private final String username;
    private final String email;

    public UserAlreadyExistsException(String message, String username, String email) {
        super(message);
        this.username = username;
        this.email = email;
    }

    public UserAlreadyExistsException(String message, String username, String email, Throwable cause) {
        super(message, cause);
        this.username = username;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
}
