package com.example.tfgbackend.user.dto;

import com.example.tfgbackend.enums.UserRole;

public record AdminUpdateUserRequest(
        String name,
        String phone,
        UserRole role,
        Boolean active
) {}
