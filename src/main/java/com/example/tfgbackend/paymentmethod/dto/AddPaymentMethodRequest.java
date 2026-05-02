package com.example.tfgbackend.paymentmethod.dto;

import com.example.tfgbackend.paymentmethod.CardType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddPaymentMethodRequest(
        @NotNull CardType cardType,
        @NotBlank String last4,
        @Min(1) @Max(12) int expiryMonth,
        @Min(2025) int expiryYear,
        @NotBlank String cardholderName
) {
}
