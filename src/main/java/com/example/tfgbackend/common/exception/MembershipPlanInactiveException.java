package com.example.tfgbackend.common.exception;

public class MembershipPlanInactiveException extends RuntimeException {

    public MembershipPlanInactiveException(Long id) {
        super("Membership plan " + id + " is not active");
    }
}
