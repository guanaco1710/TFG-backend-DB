package com.example.tfgbackend.booking.dto;

import java.time.OffsetDateTime;

public record ClassSessionSummary(
        Long id,
        ClassTypeRef classType,
        OffsetDateTime startTime,
        GymRef gym
) {}
