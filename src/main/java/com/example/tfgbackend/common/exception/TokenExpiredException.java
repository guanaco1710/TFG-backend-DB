package com.example.tfgbackend.common.exception;

/**
 * Thrown when a refresh token has passed its {@code expires_at} timestamp.
 * Maps to HTTP 401 Unauthorized.
 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("Token has expired");
    }
}
