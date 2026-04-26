package com.example.tfgbackend.common.exception;

/**
 * Thrown when a registration attempt uses an email address that is already in use.
 * Maps to HTTP 409 Conflict.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
