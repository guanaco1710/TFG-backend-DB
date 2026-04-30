package com.example.tfgbackend.common.exception;

public class RatingAlreadyExistsException extends RuntimeException {
    public RatingAlreadyExistsException(Long userId, Long sessionId) {
        super("Rating already exists for user " + userId + " and session " + sessionId);
    }
}
