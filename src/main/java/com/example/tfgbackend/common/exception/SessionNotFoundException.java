package com.example.tfgbackend.common.exception;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(Long id) {
        super("Session not found: " + id);
    }
}
