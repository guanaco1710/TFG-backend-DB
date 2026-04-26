package com.example.tfgbackend.gym.dto;

import jakarta.validation.constraints.NotBlank;

public record GymRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String city,
        String phone,
        String openingHours
) {}
