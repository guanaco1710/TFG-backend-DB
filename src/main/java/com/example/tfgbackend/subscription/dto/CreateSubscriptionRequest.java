package com.example.tfgbackend.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionRequest(
        @NotNull Long membershipPlanId,
        @NotNull Long gymId
) {}
