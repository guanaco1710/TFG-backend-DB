package com.example.tfgbackend.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    /**
     * All waitlist entries for a session ordered by position ASC.
     * Used to find the next person to promote when a spot opens up.
     */
    List<WaitlistEntry> findBySessionIdOrderByPositionAsc(Long sessionId);

    /** Check or retrieve a specific user's waitlist entry for a session. */
    Optional<WaitlistEntry> findByUserIdAndSessionId(Long userId, Long sessionId);

    /** Count how many people are waiting for a session. */
    long countBySessionId(Long sessionId);

    /** All waitlist entries for a user — "your waiting list" screen. */
    List<WaitlistEntry> findByUserId(Long userId);
}
