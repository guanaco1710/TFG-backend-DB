package com.example.tfgbackend.common.exception;

public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(Long id) {
        super("Payment method not found: " + id);
    }
}
