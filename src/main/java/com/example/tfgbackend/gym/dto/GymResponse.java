package com.example.tfgbackend.gym.dto;

public record GymResponse(
        Long id,
        String name,
        String address,
        String city,
        String phone,
        String openingHours,
        boolean active
) {}
