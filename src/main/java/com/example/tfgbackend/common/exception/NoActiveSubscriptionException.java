package com.example.tfgbackend.common.exception;

public class NoActiveSubscriptionException extends RuntimeException {

    public NoActiveSubscriptionException(Long userId) {
        super("No active subscription for user: " + userId);
    }

    public NoActiveSubscriptionException(String message) {
        super(message);
    }
}
