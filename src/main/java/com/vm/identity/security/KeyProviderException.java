package com.vm.identity.security;

/**
 * Exception thrown when key provider operations fail.
 */
public class KeyProviderException extends RuntimeException {

    public KeyProviderException(String message) {
        super(message);
    }

    public KeyProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
