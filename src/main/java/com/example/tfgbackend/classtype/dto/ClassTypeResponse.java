package com.example.tfgbackend.classtype.dto;

public record ClassTypeResponse(
        Long id,
        String name,
        String description,
        String level
) {}
