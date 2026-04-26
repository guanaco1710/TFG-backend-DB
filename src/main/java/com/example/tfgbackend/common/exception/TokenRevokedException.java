package com.example.tfgbackend.common.exception;

/**
 * Thrown when a refresh token has been revoked (either explicitly on logout,
 * or automatically when a replaced token is re-used — a theft signal).
 * Maps to HTTP 401 Unauthorized.
 */
public class TokenRevokedException extends RuntimeException {

    public TokenRevokedException() {
        super("Token has been revoked");
    }
}
