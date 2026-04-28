package com.example.tfgbackend.common.exception;

public class BookingAlreadyCancelledException extends RuntimeException {

    public BookingAlreadyCancelledException(Long id) {
        super("Booking " + id + " is already cancelled");
    }
}
