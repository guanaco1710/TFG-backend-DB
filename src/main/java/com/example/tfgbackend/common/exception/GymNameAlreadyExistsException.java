package com.example.tfgbackend.common.exception;

/**
 * Thrown when a create/update attempt uses a gym name that is already taken.
 * Maps to HTTP 409 Conflict.
 */
public class GymNameAlreadyExistsException extends RuntimeException {

    public GymNameAlreadyExistsException(String name) {
        super("Gym name already exists: " + name);
    }
}
