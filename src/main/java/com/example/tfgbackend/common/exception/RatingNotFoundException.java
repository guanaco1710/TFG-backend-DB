package com.example.tfgbackend.common.exception;

public class RatingNotFoundException extends RuntimeException {
    public RatingNotFoundException(Long id) {
        super("Rating not found: " + id);
    }
}
