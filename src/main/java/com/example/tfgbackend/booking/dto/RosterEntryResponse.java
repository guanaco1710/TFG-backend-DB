package com.example.tfgbackend.booking.dto;

import com.example.tfgbackend.enums.BookingStatus;

import java.time.Instant;

public record RosterEntryResponse(
        Long bookingId,
        BookingStatus status,
        Instant bookedAt,
        Long userId,
        String userFullName,
        String userEmail
) {}
