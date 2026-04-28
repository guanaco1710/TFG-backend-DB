package com.example.tfgbackend.common.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Booking not found: " + id);
    }
}
