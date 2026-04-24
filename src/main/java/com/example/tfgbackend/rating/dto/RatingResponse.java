package com.example.tfgbackend.rating.dto;

import java.time.Instant;

public record RatingResponse(
        Long id,
        int score,
        String comment,
        Instant ratedAt,
        Long userId,
        Long sessionId
) {}
