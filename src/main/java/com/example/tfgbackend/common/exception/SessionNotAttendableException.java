package com.example.tfgbackend.common.exception;

import com.example.tfgbackend.enums.SessionStatus;

public class SessionNotAttendableException extends RuntimeException {

    public SessionNotAttendableException(Long sessionId, SessionStatus status) {
        super("Session " + sessionId + " cannot record attendance in status: " + status);
    }
}
