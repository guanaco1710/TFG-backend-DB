package com.example.tfgbackend.booking.dto;

import java.time.LocalDateTime;

public record ClassSessionSummary(
        Long id,
        String classTypeName,
        LocalDateTime startTime,
        String gymName
) {}
