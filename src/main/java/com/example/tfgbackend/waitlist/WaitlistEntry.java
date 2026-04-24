package com.example.tfgbackend.waitlist;

import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.common.BaseEntity;
import com.example.tfgbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A user's position in the waitlist for a fully-booked {@link ClassSession}.
 *
 * <p>When a booking is cancelled and a spot opens up, the service promotes the
 * entry with the lowest {@code position} to a CONFIRMED {@link com.example.tfgbackend.booking.Booking}
 * and renumbers (or simply removes) the remaining waitlist entries.
 *
 * <p>The unique constraint {@code (user_id, session_id)} prevents a user from
 * queuing twice for the same session.
 */
@Entity
@Table(
    name = "waitlist",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_waitlist_user_session",
        columnNames = {"user_id", "session_id"}
    ),
    indexes = {
        @Index(name = "idx_waitlist_session_id", columnList = "session_id"),
        @Index(name = "idx_waitlist_user_id",    columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry extends BaseEntity {

    /** Queue position — lower number means higher priority.  Service keeps this compact. */
    @Column(name = "position", nullable = false)
    private int position;

    /** The moment the user joined the waitlist. */
    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;
}
