package com.example.tfgbackend.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(@NotNull Long sessionId) {}
