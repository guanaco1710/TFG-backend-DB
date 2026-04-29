package com.example.tfgbackend.common.exception;

public class SubscriptionAlreadyActiveException extends RuntimeException {

    public SubscriptionAlreadyActiveException(Long userId) {
        super("User " + userId + " already has an active subscription");
    }
}
