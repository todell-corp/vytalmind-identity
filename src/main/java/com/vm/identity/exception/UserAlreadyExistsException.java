package com.vm.identity.exception;

/**
 * Exception thrown when attempting to create or update a user with an email that already exists.
 */
public class UserAlreadyExistsException extends RuntimeException {

    private final String email;

    public UserAlreadyExistsException(String message, String email) {
        super(message);
        this.email = email;
    }

    public UserAlreadyExistsException(String message, String email, Throwable cause) {
        super(message, cause);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
