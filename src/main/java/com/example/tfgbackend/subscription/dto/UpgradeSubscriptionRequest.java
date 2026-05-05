package com.example.tfgbackend.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record UpgradeSubscriptionRequest(
        @NotNull Long newMembershipPlanId
) {}
