package com.example.tfgbackend.subscription.dto;

import java.math.BigDecimal;

public record MembershipPlanSummary(
        Long id,
        String name,
        BigDecimal priceMonthly
) {}
