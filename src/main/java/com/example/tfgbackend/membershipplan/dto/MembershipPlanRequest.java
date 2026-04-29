package com.example.tfgbackend.membershipplan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MembershipPlanRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0.0") BigDecimal priceMonthly,
        Integer classesPerMonth,
        boolean allowsWaitlist,
        @NotNull @Min(1) @Max(60) Integer durationMonths
) {}
