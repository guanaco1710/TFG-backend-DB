package com.example.tfgbackend.common.exception;

public class MembershipPlanInUseException extends RuntimeException {

    public MembershipPlanInUseException(Long id) {
        super("Membership plan " + id + " is in use by active subscriptions");
    }
}
