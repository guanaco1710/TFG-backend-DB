package com.example.tfgbackend.common.exception;

public class NoActiveSubscriptionException extends RuntimeException {

    public NoActiveSubscriptionException(String message) {
        super(message);
    }
}
