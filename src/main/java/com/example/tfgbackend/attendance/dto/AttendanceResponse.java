package com.example.tfgbackend.attendance.dto;

import com.example.tfgbackend.enums.AttendanceStatus;

import java.time.Instant;

public record AttendanceResponse(
        Long id,
        Long userId,
        Long sessionId,
        AttendanceStatus status,
        Instant recordedAt
) {}
