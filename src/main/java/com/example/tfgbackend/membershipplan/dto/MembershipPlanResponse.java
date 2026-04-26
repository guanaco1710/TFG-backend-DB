package com.example.tfgbackend.membershipplan.dto;

import java.math.BigDecimal;

public record MembershipPlanResponse(
        Long id,
        String name,
        String description,
        BigDecimal priceMonthly,
        Integer classesPerMonth,
        boolean allowsWaitlist,
        boolean active
) {}
