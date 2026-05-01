package com.example.tfgbackend.notification;

import com.example.tfgbackend.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n
             WHERE n.sent = false
               AND n.scheduledAt <= :now
             ORDER BY n.scheduledAt ASC
            """)
    List<Notification> findPendingDue(@Param("now") Instant now);

    List<Notification> findByUserIdOrderByScheduledAtDesc(Long userId);

    List<Notification> findBySessionId(Long sessionId);

    List<Notification> findBySessionIdOrderByScheduledAtDesc(Long sessionId);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.session.id = :sessionId AND n.sent = false")
    void deleteUnsentByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.session.id = :sessionId AND n.sent = false")
    void deleteUnsentBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllReadByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
              AND (:type IS NULL OR n.type = :type)
              AND (:unreadOnly = false OR n.read = false)
              AND (:sentOnly = false OR n.sent = true)
            ORDER BY n.scheduledAt DESC
            """)
    Page<Notification> findByUserIdAndFilters(
            @Param("userId") Long userId,
            @Param("type") NotificationType type,
            @Param("unreadOnly") Boolean unreadOnly,
            @Param("sentOnly") Boolean sentOnly,
            Pageable pageable);
}
