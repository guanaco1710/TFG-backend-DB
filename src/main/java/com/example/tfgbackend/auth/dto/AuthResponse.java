package com.example.tfgbackend.auth.dto;

/** Response body for register and login endpoints. */
public record AuthResponse(
        TokenPair tokens,
        UserSummary user
) {}
