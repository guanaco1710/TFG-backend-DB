package com.example.tfgbackend.user.dto;

public record UpdateUserRequest(
        String name,
        String phone,
        String specialty
) {}
