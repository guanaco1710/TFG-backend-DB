package com.example.tfgbackend.common.exception;

public class SpecialtyNotAllowedException extends RuntimeException {

    public SpecialtyNotAllowedException() {
        super("specialty can only be set for users with role INSTRUCTOR");
    }
}
