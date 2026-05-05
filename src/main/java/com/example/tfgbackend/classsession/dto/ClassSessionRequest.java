package com.example.tfgbackend.classsession.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record ClassSessionRequest(
        @NotNull Long classTypeId,
        Long gymId,
        Long instructorId,
        @NotNull OffsetDateTime startTime,
        @Min(1) int durationMinutes,
        @Min(1) int maxCapacity,
        String room
) {}
