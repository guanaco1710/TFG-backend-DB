package com.example.tfgbackend.common.exception;

public class ClassFullException extends RuntimeException {

    public ClassFullException(Long sessionId) {
        super("Session " + sessionId + " is full. Join the waitlist.");
    }
}
