package com.example.tfgbackend.auth.dto;

/** Access + refresh token pair returned after successful auth operations. */
public record TokenPair(
        String accessToken,
        String refreshToken
) {}
