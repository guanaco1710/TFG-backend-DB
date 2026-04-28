package com.example.tfgbackend.common.exception;

public class AlreadyOnWaitlistException extends RuntimeException {

    public AlreadyOnWaitlistException(Long userId, Long sessionId) {
        super("User " + userId + " is already on the waitlist for session " + sessionId);
    }
}
