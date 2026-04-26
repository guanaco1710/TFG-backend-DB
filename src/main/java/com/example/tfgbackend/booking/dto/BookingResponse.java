package com.example.tfgbackend.booking.dto;

import com.example.tfgbackend.enums.BookingStatus;

import java.time.Instant;

public record BookingResponse(
        Long id,
        ClassSessionSummary classSession,
        BookingStatus status,
        Instant bookedAt
) {}
