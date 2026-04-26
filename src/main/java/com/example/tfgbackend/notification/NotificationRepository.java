package com.example.tfgbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Fetch all unsent notifications whose scheduled time has passed.
     * Called by the notification dispatcher job.
     */
    @Query("""
            SELECT n FROM Notification n
             WHERE n.sent = false
               AND n.scheduledAt <= :now
             ORDER BY n.scheduledAt ASC
            """)
    List<Notification> findPendingDue(@Param("now") Instant now);

    /** A user's full notification history. */
    List<Notification> findByUserIdOrderByScheduledAtDesc(Long userId);

    /** All notifications linked to a specific session (e.g. to cancel them on session cancellation). */
    List<Notification> findBySessionId(Long sessionId);
}
