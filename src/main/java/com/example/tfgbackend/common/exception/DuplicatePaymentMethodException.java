package com.example.tfgbackend.common.exception;

import com.example.tfgbackend.paymentmethod.CardType;

public class DuplicatePaymentMethodException extends RuntimeException {

    public DuplicatePaymentMethodException() {
        super("A card with the same type and last 4 digits already exists");
    }

    public DuplicatePaymentMethodException(Long userId, CardType cardType, String last4) {
        super("A card with the same type and last 4 digits already exists");
    }
}
