package com.example.tfgbackend.auth.dto;

import com.example.tfgbackend.enums.UserRole;

/** Minimal user info embedded in auth responses. */
public record UserSummary(
        Long id,
        String name,
        String email,
        UserRole role
) {}
