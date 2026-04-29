package com.example.tfgbackend.common.exception;

public class ClassTypeNotFoundException extends RuntimeException {

    public ClassTypeNotFoundException(Long id) {
        super("ClassType not found: " + id);
    }
}
