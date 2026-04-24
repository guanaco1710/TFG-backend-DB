package com.example.tfgbackend.attendance.dto;

import com.example.tfgbackend.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceEntryRequest(
        @NotNull Long userId,
        @NotNull AttendanceStatus status
) {}
