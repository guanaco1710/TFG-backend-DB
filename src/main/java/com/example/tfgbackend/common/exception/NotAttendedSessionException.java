package com.example.tfgbackend.common.exception;

public class NotAttendedSessionException extends RuntimeException {
    public NotAttendedSessionException(Long userId, Long sessionId) {
        super("User " + userId + " has not attended session " + sessionId);
    }
}
