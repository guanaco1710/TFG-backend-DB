package com.example.tfgbackend.stats.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserStatsResponse(
        long totalBookings,
        long totalAttended,
        long totalNoShows,
        long totalCancellations,
        double attendanceRate,
        int currentStreak,
        String favoriteClassType,
        long classesBookedThisMonth,
        Integer classesRemainingThisMonth
) {}
