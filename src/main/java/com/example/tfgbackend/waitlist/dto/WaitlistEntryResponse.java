package com.example.tfgbackend.waitlist.dto;

import com.example.tfgbackend.booking.dto.ClassSessionSummary;

import java.time.Instant;

public record WaitlistEntryResponse(
        Long id,
        ClassSessionSummary classSession,
        Long userId,
        int position,
        Instant joinedAt
) {}
