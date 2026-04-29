package com.example.tfgbackend.common.exception;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(Long id) {
        super("Subscription not found: " + id);
    }
}
