package com.example.tfgbackend.stats.dto;

import com.example.tfgbackend.enums.AttendanceStatus;

import java.time.Instant;

public record AttendanceHistoryEntry(
        Long id,
        Instant recordedAt,
        AttendanceStatus status,
        ClassSessionInfo classSession
) {}
