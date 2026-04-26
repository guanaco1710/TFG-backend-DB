package com.example.tfgbackend.membershipplan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MembershipPlanRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0.0") BigDecimal priceMonthly,
        Integer classesPerMonth,
        boolean allowsWaitlist
) {}
