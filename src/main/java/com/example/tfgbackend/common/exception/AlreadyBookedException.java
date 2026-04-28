package com.example.tfgbackend.common.exception;

public class AlreadyBookedException extends RuntimeException {

    public AlreadyBookedException(Long userId, Long sessionId) {
        super("User " + userId + " already has an active booking for session " + sessionId);
    }
}
