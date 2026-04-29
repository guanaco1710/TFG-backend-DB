package com.example.tfgbackend.gym.dto;

import java.time.Instant;

public record GymResponse(
        Long id,
        String name,
        String address,
        String city,
        String phone,
        String openingHours,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
