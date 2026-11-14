package com.dvms.service;

/**
 * Exception thrown when file storage operations fail.
 */
public class StorageException extends Exception {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
