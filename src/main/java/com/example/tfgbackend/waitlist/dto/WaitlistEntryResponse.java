package com.example.tfgbackend.waitlist.dto;

import java.time.Instant;

public record WaitlistEntryResponse(
        Long id,
        Long sessionId,
        Long userId,
        int position,
        Instant joinedAt
) {}
