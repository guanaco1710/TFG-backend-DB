package com.example.tfgbackend.notification;

import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.common.BaseEntity;
import com.example.tfgbackend.enums.NotificationType;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A push/email notification queued for delivery to a {@link User}.
 *
 * <p>{@code sessionId} is nullable because some notifications are account-level
 * (e.g. subscription expiry reminders) rather than session-specific.
 *
 * <p>A background job selects rows where {@code sent = false AND scheduledAt <= now()}
 * and dispatches them.  The {@code idx_notification_sent} and
 * {@code idx_notification_scheduled_at} indexes (created in V2) support this query.
 */
@Entity
@Table(
    name = "notification",
    indexes = {
        @Index(name = "idx_notification_sent",         columnList = "sent"),
        @Index(name = "idx_notification_scheduled_at", columnList = "scheduled_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    /** When the notification should be dispatched. */
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    /** {@code true} once the notification has been sent. */
    @Column(name = "sent", nullable = false)
    @Builder.Default
    private boolean sent = false;

    /** The moment the notification was actually sent; null until then. */
    @Column(name = "sent_at")
    private Instant sentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The related session, if any.  Null for account-level notifications.
     * The FK is ON DELETE SET NULL in the migration, so this becomes null if the session
     * is ever deleted.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ClassSession session;
}
