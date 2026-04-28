package com.example.tfgbackend.common.exception;

public class WaitlistEntryNotFoundException extends RuntimeException {

    public WaitlistEntryNotFoundException(Long userId, Long sessionId) {
        super("No waitlist entry for user " + userId + " in session " + sessionId);
    }
}
