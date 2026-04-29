package com.example.tfgbackend.common.exception;

public class MonthlyClassLimitReachedException extends RuntimeException {

    public MonthlyClassLimitReachedException(String message) {
        super(message);
    }
}
