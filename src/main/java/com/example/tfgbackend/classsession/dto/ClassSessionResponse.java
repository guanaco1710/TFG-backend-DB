package com.example.tfgbackend.classsession.dto;

import com.example.tfgbackend.enums.SessionStatus;

import java.time.LocalDateTime;

public record ClassSessionResponse(
        Long id,
        ClassTypeSummary classType,
        GymSummary gym,
        InstructorSummary instructor,
        LocalDateTime startTime,
        int durationMinutes,
        int maxCapacity,
        String room,
        SessionStatus status,
        int confirmedCount,
        int availableSpots
) {}
