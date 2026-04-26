package com.example.tfgbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/refresh}. */
public record RefreshRequest(
        @NotBlank String refreshToken
) {}
