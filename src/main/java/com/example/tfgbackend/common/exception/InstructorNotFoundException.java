package com.example.tfgbackend.common.exception;

public class InstructorNotFoundException extends RuntimeException {

    public InstructorNotFoundException(Long id) {
        super("Instructor not found: " + id);
    }
}
