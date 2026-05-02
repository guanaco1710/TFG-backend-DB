package com.example.tfgbackend.common.exception;

public class CardExpiredException extends RuntimeException {

    public CardExpiredException() {
        super("Card is expired");
    }

    public CardExpiredException(int expiryMonth, int expiryYear) {
        super("Card expired: " + expiryMonth + "/" + expiryYear);
    }
}
