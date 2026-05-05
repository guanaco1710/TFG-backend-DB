package com.example.tfgbackend.booking.dto;

import java.time.OffsetDateTime;

public record ClassSessionSummary(
        Long id,
        String classTypeName,
        OffsetDateTime startTime,
        String gymName
) {}
