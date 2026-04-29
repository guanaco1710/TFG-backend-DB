package com.example.tfgbackend.common.exception;

public class MembershipPlanNotFoundException extends RuntimeException {

    public MembershipPlanNotFoundException(Long id) {
        super("Membership plan not found: " + id);
    }
}
