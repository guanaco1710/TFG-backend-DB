package com.example.tfgbackend.common.exception;

public class ClassTypeNameAlreadyExistsException extends RuntimeException {

    public ClassTypeNameAlreadyExistsException(String name) {
        super("ClassType name already exists: " + name);
    }
}
