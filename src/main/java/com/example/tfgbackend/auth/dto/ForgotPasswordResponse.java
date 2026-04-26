package com.example.tfgbackend.auth.dto;

public record ForgotPasswordResponse(
        String message,
        String resetToken
) {}
