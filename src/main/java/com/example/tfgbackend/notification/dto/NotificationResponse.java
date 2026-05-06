package com.example.tfgbackend.notification.dto;

import com.example.tfgbackend.enums.NotificationType;

import java.time.Instant;
import java.time.OffsetDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Instant scheduledAt,
        boolean sent,
        Instant sentAt,
        boolean read,
        Long userId,
        SessionSummary session
) {
    public record SessionSummary(Long id, OffsetDateTime startTime, ClassTypeSummary classType) {}
    public record ClassTypeSummary(String name) {}
}
