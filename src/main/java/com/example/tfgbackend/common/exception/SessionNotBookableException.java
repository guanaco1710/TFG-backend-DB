package com.example.tfgbackend.common.exception;

public class SessionNotBookableException extends RuntimeException {

    public SessionNotBookableException(Long sessionId) {
        super("Session " + sessionId + " is not open for booking");
    }
}
