package com.example.tfgbackend.auth;

import com.example.tfgbackend.enums.UserRole;

/**
 * Lightweight principal placed in the {@link org.springframework.security.core.context.SecurityContext}
 * after JWT validation. Avoids a database round-trip on every request.
 */
public record AuthenticatedUser(Long userId, String email, UserRole role) {}
