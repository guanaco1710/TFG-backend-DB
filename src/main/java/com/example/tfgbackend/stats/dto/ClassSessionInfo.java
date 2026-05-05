package com.example.tfgbackend.stats.dto;

import java.time.OffsetDateTime;

public record ClassSessionInfo(
        Long id,
        OffsetDateTime startTime,
        int durationMinutes,
        String room,
        ClassTypeInfo classType
) {}
