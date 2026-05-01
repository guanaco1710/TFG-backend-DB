package com.example.tfgbackend.notification.dto;

import com.example.tfgbackend.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Instant scheduledAt,
        boolean sent,
        Instant sentAt,
        boolean read,
        Long userId,
        Long sessionId
) {}
