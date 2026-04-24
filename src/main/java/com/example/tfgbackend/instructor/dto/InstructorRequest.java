package com.example.tfgbackend.instructor.dto;

import jakarta.validation.constraints.NotBlank;

public record InstructorRequest(
        @NotBlank String name,
        String specialty
) {}
