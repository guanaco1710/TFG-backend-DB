package com.example.tfgbackend.classsession.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ClassSessionRequest(
        @NotNull Long classTypeId,
        Long gymId,
        Long instructorId,
        @NotNull LocalDateTime startTime,
        @Min(1) int durationMinutes,
        @Min(1) int maxCapacity,
        String room
) {}
