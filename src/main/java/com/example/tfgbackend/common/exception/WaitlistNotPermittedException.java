package com.example.tfgbackend.common.exception;

public class WaitlistNotPermittedException extends RuntimeException {

    public WaitlistNotPermittedException() {
        super("Your membership plan does not allow waitlist access");
    }
}
