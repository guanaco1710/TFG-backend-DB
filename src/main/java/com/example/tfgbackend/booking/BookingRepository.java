package com.example.tfgbackend.booking;

import com.example.tfgbackend.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** All bookings for a user, latest first — used on the user's "my bookings" screen. */
    Page<Booking> findByUserIdOrderByBookedAtDesc(Long userId, Pageable pageable);

    /** All bookings for a session — used by the instructor roster view. */
    Page<Booking> findBySessionId(Long sessionId, Pageable pageable);

    /** Find a specific booking by user and session (used for duplicate check and cancellation). */
    Optional<Booking> findByUserIdAndSessionId(Long userId, Long sessionId);

    /**
     * Count non-cancelled bookings for a session — used for capacity enforcement.
     * CONFIRMED, ATTENDED, and NO_SHOW all occupy a physical seat.
     */
    @Query("""
            SELECT COUNT(b) FROM Booking b
             WHERE b.session.id = :sessionId
               AND b.status IN (
                     com.example.tfgbackend.enums.BookingStatus.CONFIRMED,
                     com.example.tfgbackend.enums.BookingStatus.ATTENDED,
                     com.example.tfgbackend.enums.BookingStatus.NO_SHOW
                   )
            """)
    long countConfirmedBySessionId(@Param("sessionId") Long sessionId);

    /** User's booking history filtered by status. */
    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);
}
