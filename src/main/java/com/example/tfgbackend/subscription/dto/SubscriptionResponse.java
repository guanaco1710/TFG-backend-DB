package com.example.tfgbackend.subscription.dto;

import com.example.tfgbackend.enums.SubscriptionStatus;

import java.time.LocalDate;

public record SubscriptionResponse(
        Long id,
        MembershipPlanSummary plan,
        SubscriptionStatus status,
        LocalDate startDate,
        LocalDate renewalDate,
        int classesUsedThisMonth,
        Integer classesRemainingThisMonth
) {}
