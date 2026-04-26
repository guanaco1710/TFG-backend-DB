package com.example.tfgbackend.user.dto;

import com.example.tfgbackend.enums.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        UserRole role,
        boolean active,
        Instant createdAt
) {}
