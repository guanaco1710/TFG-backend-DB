package com.example.tfgbackend.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateRatingRequest(
        @NotNull Long sessionId,
        @Min(1) @Max(5) int score,
        String comment
) {}
