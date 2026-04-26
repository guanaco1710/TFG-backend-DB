package com.example.tfgbackend.attendance.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecordAttendanceRequest(
        @NotEmpty List<AttendanceEntryRequest> attendances
) {}
