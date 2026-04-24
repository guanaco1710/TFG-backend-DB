package com.example.tfgbackend.classtype.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClassTypeRequest(
        @NotBlank String name,
        String description,
        @NotBlank @Pattern(regexp = "BASIC|INTERMEDIATE|ADVANCED") String level
) {}
