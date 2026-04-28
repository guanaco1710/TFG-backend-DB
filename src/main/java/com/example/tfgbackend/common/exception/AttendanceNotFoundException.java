package com.example.tfgbackend.common.exception;

public class AttendanceNotFoundException extends RuntimeException {

    public AttendanceNotFoundException(Long id) {
        super("Attendance record not found: " + id);
    }
}
