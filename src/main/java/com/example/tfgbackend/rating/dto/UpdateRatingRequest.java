package com.example.tfgbackend.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateRatingRequest(
        @Min(1) @Max(5) int score,
        @Size(max = 1000) String comment
) {}
