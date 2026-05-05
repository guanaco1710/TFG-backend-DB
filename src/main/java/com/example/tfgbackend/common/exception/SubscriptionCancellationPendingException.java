package com.example.tfgbackend.common.exception;

public class SubscriptionCancellationPendingException extends RuntimeException {

    public SubscriptionCancellationPendingException(Long id) {
        super("Subscription " + id + " is already scheduled for cancellation at period end");
    }
}
