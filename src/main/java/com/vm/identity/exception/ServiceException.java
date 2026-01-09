package com.vm.identity.exception;

/**
 * Exception thrown when a downstream service call fails.
 */
public class ServiceException extends RuntimeException {

    private final int statusCode;

    public ServiceException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public ServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public ServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
