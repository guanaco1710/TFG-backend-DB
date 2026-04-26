package com.example.tfgbackend.common.exception;

/**
 * Thrown when login credentials are wrong (email not found or password mismatch).
 *
 * <p>Deliberately uses the same message for both cases to prevent user-enumeration attacks.
 * Maps to HTTP 401 Unauthorized.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
