package com.example.tfgbackend.gym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GymRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200) String address,
        @NotBlank @Size(max = 100) String city,
        // Pattern is applied only when the value is non-null/non-blank (nullable field)
        @Size(max = 15) @Pattern(regexp = "^\\+?[0-9 \\-]{6,15}$", message = "must be a valid phone number") String phone,
        @Size(max = 200) String openingHours
) {}
