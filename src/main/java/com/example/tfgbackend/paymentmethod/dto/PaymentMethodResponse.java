package com.example.tfgbackend.paymentmethod.dto;

import com.example.tfgbackend.paymentmethod.CardType;

import java.time.Instant;

public record PaymentMethodResponse(
        Long id,
        CardType cardType,
        String last4,
        int expiryMonth,
        int expiryYear,
        String cardholderName,
        boolean isDefault,
        Instant createdAt
) {
}
