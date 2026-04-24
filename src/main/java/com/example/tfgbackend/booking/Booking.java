package com.example.tfgbackend.booking;

import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.common.BaseEntity;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's reservation in a {@link ClassSession}.
 *
 * <p>The unique constraint {@code (user_id, session_id)} is the last line of defence
 * against double-booking.  The service should also check capacity before inserting,
 * but the constraint guarantees correctness even under high concurrency.
 *
 * <p>Cancellation is a state change (status → CANCELLED), never a physical delete,
 * so the record remains available for audit and statistics.
 *
 * <p>The WAITLISTED state is not used here — waitlist entries live in the separate
 * {@code waitlist} table.  A booking transitions directly to CONFIRMED when the
 * user is promoted from the waitlist.
 */
@Entity
@Table(
    name = "booking",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_booking_user_session",
        columnNames = {"user_id", "session_id"}
    ),
    indexes = {
        @Index(name = "idx_booking_user_id",    columnList = "user_id"),
        @Index(name = "idx_booking_session_id", columnList = "session_id"),
        @Index(name = "idx_booking_status",     columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    /** The moment the booking was made. Defaults to now at insert time. */
    @Column(name = "booked_at", nullable = false)
    @Builder.Default
    private Instant bookedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    /** The member who made this booking.  Many bookings per user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The session being booked.  Many bookings per session (up to maxCapacity). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;
}
