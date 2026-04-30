package com.example.tfgbackend.common.exception;

public class ClassTypeInUseException extends RuntimeException {

    public ClassTypeInUseException(Long id) {
        super("ClassType " + id + " is referenced by one or more class sessions and cannot be deleted");
    }
}
