package com.example.tfgbackend.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumsTest {

    @Test
    void attendanceStatus_values() {
        assertThat(AttendanceStatus.values()).containsExactly(AttendanceStatus.ATTENDED, AttendanceStatus.NO_SHOW);
        assertThat(AttendanceStatus.valueOf("ATTENDED")).isEqualTo(AttendanceStatus.ATTENDED);
    }

    @Test
    void bookingStatus_values() {
        assertThat(BookingStatus.values()).containsExactly(
                BookingStatus.CONFIRMED, BookingStatus.WAITLISTED, BookingStatus.CANCELLED,
                BookingStatus.ATTENDED, BookingStatus.NO_SHOW);
        assertThat(BookingStatus.valueOf("CONFIRMED")).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void notificationType_values() {
        assertThat(NotificationType.values()).containsExactly(
                NotificationType.REMINDER, NotificationType.CONFIRMATION, NotificationType.CANCELLATION);
        assertThat(NotificationType.valueOf("REMINDER")).isEqualTo(NotificationType.REMINDER);
    }

    @Test
    void sessionStatus_values() {
        assertThat(SessionStatus.values()).containsExactly(
                SessionStatus.SCHEDULED, SessionStatus.ACTIVE, SessionStatus.CANCELLED, SessionStatus.FINISHED);
        assertThat(SessionStatus.valueOf("SCHEDULED")).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void subscriptionStatus_values() {
        assertThat(SubscriptionStatus.values()).containsExactly(
                SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED, SubscriptionStatus.EXPIRED);
        assertThat(SubscriptionStatus.valueOf("ACTIVE")).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void userRole_values() {
        assertThat(UserRole.values()).containsExactly(UserRole.CUSTOMER, UserRole.INSTRUCTOR, UserRole.ADMIN);
        assertThat(UserRole.valueOf("CUSTOMER")).isEqualTo(UserRole.CUSTOMER);
    }
}
