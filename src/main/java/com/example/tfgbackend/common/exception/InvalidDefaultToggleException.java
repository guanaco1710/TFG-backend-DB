package com.example.tfgbackend.common.exception;

public class InvalidDefaultToggleException extends RuntimeException {

    public InvalidDefaultToggleException() {
        super("Cannot set isDefault to false — set another card as default instead");
    }
}
