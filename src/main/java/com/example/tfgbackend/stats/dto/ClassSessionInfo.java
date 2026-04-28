package com.example.tfgbackend.stats.dto;

import java.time.LocalDateTime;

public record ClassSessionInfo(
        Long id,
        LocalDateTime startTime,
        int durationMinutes,
        String room,
        ClassTypeInfo classType
) {}
