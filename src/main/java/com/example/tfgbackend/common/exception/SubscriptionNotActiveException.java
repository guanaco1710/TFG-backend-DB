package com.example.tfgbackend.common.exception;

public class SubscriptionNotActiveException extends RuntimeException {

    public SubscriptionNotActiveException(Long id) {
        super("Subscription " + id + " is not active");
    }
}
