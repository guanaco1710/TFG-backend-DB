package com.example.tfgbackend.common.exception;

public class GymNotFoundException extends RuntimeException {

    public GymNotFoundException(Long id) {
        super("Gym not found: " + id);
    }
}
