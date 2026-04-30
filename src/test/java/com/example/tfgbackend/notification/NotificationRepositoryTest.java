package com.example.tfgbackend.notification;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.enums.NotificationType;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test for {@link NotificationRepository}.
 *
 * Covers both the methods that already exist on the interface and the new
 * query methods the service will add to support the inbox, unread-count,
 * filtered listing, and cleanup operations.
 *
 * Uses a real PostgreSQL container (via {@link AbstractRepositoryTest}) —
 * never H2.
 */
class NotificationRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    NotificationRepository repository;

    private User alice;
    private User bob;
    private ClassSession session;
    private ClassSession otherSession;

    @BeforeEach
    void setUp() {
        ClassType classType = em.persistAndFlush(ClassType.builder()
                .name("Spinning")
                .description("High-intensity cycling")
                .level("INTERMEDIATE")
                .build());

        session = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45)
                .maxCapacity(10)
                .room("1A")
                .classType(classType)
                .build());

        otherSession = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(2))
                .durationMinutes(60)
                .maxCapacity(15)
                .room("2B")
                .classType(classType)
                .build());

        alice = em.persistAndFlush(User.builder()
                .name("Alice")
                .email("alice@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .build());

        bob = em.persistAndFlush(User.builder()
                .name("Bob")
                .email("bob@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .build());

        em.clear();
    }

    // ---------------------------------------------------------------------------
    // findPendingDue
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findPendingDue")
    class FindPendingDue {

        @Test
        @DisplayName("unsent notification scheduled in the past — returned by query")
        void findPendingDue_UnsentPastNotification_IsReturned() {
            Notification n = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            List<Notification> result = repository.findPendingDue(Instant.now());

            assertThat(result).extracting(r -> r.getId()).contains(n.getId());
        }

        @Test
        @DisplayName("already-sent notification — not returned even if scheduledAt is past")
        void findPendingDue_AlreadySentNotification_NotReturned() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(120))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(100))
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            List<Notification> result = repository.findPendingDue(Instant.now());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("future-scheduled unsent notification — not returned")
        void findPendingDue_FutureUnsentNotification_NotReturned() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            List<Notification> result = repository.findPendingDue(Instant.now());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("multiple unsent past notifications — all returned ordered by scheduledAt ASC")
        void findPendingDue_MultipleUnsentPast_ReturnedInAscOrder() {
            Instant earlier = Instant.now().minusSeconds(200);
            Instant later   = Instant.now().minusSeconds(100);

            Notification n1 = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(later)
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());

            Notification n2 = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(earlier)
                    .sent(false)
                    .user(bob)
                    .session(session)
                    .build());

            em.clear();

            List<Notification> result = repository.findPendingDue(Instant.now());

            assertThat(result).hasSize(2);
            // Ordered by scheduledAt ASC → n2 (earlier) comes first
            assertThat(result.get(0).getId()).isEqualTo(n2.getId());
            assertThat(result.get(1).getId()).isEqualTo(n1.getId());
        }
    }

    // ---------------------------------------------------------------------------
    // findByUserIdOrderByScheduledAtDesc
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdOrderByScheduledAtDesc")
    class FindByUserIdOrderByScheduledAtDesc {

        @Test
        @DisplayName("user with notifications — returns all, most recent first")
        void findByUserId_UserWithNotifications_ReturnedDescByScheduledAt() {
            Instant older  = Instant.now().minusSeconds(3600);
            Instant newer  = Instant.now().minusSeconds(60);

            Notification nOlder = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(older)
                    .sent(true)
                    .sentAt(older)
                    .user(alice)
                    .session(session)
                    .build());

            Notification nNewer = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(newer)
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());

            em.clear();

            List<Notification> result = repository.findByUserIdOrderByScheduledAtDesc(alice.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(nNewer.getId());
            assertThat(result.get(1).getId()).isEqualTo(nOlder.getId());
        }

        @Test
        @DisplayName("other user's notifications not included")
        void findByUserId_OnlyReturnsOwnNotifications() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(false)
                    .user(bob)
                    .session(session)
                    .build());
            em.clear();

            List<Notification> result = repository.findByUserIdOrderByScheduledAtDesc(alice.getId());

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // findBySessionId (existing)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findBySessionId(Long)")
    class FindBySessionId {

        @Test
        @DisplayName("existing session with notifications — returns all matching")
        void findBySessionId_ExistingSession_ReturnsAll() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(60))
                    .sent(false)
                    .user(bob)
                    .session(session)
                    .build());

            // Noise: different session
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CANCELLATION)
                    .scheduledAt(Instant.now())
                    .sent(false)
                    .user(alice)
                    .session(otherSession)
                    .build());

            em.clear();

            List<Notification> result = repository.findBySessionId(session.getId());

            assertThat(result).hasSize(2)
                    .allMatch(n -> n.getSession().getId().equals(session.getId()));
        }

        @Test
        @DisplayName("session with no notifications — returns empty list")
        void findBySessionId_NoNotifications_ReturnsEmpty() {
            em.clear();

            List<Notification> result = repository.findBySessionId(session.getId());

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // findBySessionIdOrderByScheduledAtDesc (new — admin view)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findBySessionIdOrderByScheduledAtDesc")
    class FindBySessionIdOrderByScheduledAtDesc {

        @Test
        @DisplayName("session with two notifications — returned most-recent first")
        void findBySessionIdOrderByScheduledAtDesc_TwoNotifications_DescOrder() {
            Instant older = Instant.now().minusSeconds(3600);
            Instant newer = Instant.now().minusSeconds(60);

            Notification nOlder = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(older)
                    .sent(true)
                    .sentAt(older)
                    .user(alice)
                    .session(session)
                    .build());

            Notification nNewer = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(newer)
                    .sent(false)
                    .user(bob)
                    .session(session)
                    .build());

            em.clear();

            List<Notification> result =
                    repository.findBySessionIdOrderByScheduledAtDesc(session.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(nNewer.getId());
            assertThat(result.get(1).getId()).isEqualTo(nOlder.getId());
        }

        @Test
        @DisplayName("session with no notifications — returns empty list")
        void findBySessionIdOrderByScheduledAtDesc_NoNotifications_ReturnsEmpty() {
            List<Notification> result =
                    repository.findBySessionIdOrderByScheduledAtDesc(session.getId());

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // countByUserIdAndReadFalse (new — unread count)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("countByUserIdAndReadFalse")
    class CountByUserIdAndReadFalse {

        @Test
        @DisplayName("user with two unread and one read — returns 2")
        void countByUserIdAndReadFalse_TwoUnread_Returns2() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(alice)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(alice)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CANCELLATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(true)
                    .user(alice)
                    .build());

            em.clear();

            long count = repository.countByUserIdAndReadFalse(alice.getId());

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("other user's unread notifications not counted")
        void countByUserIdAndReadFalse_OtherUserUnread_NotCounted() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(bob)
                    .build());
            em.clear();

            long count = repository.countByUserIdAndReadFalse(alice.getId());

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("user with all read — returns 0")
        void countByUserIdAndReadFalse_AllRead_ReturnsZero() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(true)
                    .user(alice)
                    .build());
            em.clear();

            long count = repository.countByUserIdAndReadFalse(alice.getId());

            assertThat(count).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // deleteUnsentByUserIdAndSessionId (new — booking cancel cleanup)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteUnsentByUserIdAndSessionId")
    class DeleteUnsentByUserIdAndSessionId {

        @Test
        @DisplayName("unsent notification for user+session — deleted")
        void deleteUnsentByUserIdAndSessionId_UnsentExists_Deleted() {
            Notification unsent = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            repository.deleteUnsentByUserIdAndSessionId(alice.getId(), session.getId());
            em.flush();

            assertThat(repository.findById(unsent.getId())).isEmpty();
        }

        @Test
        @DisplayName("already-sent notification for user+session — not deleted")
        void deleteUnsentByUserIdAndSessionId_SentExists_NotDeleted() {
            Notification sent = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(50))
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            repository.deleteUnsentByUserIdAndSessionId(alice.getId(), session.getId());
            em.flush();

            assertThat(repository.findById(sent.getId())).isPresent();
        }

        @Test
        @DisplayName("notification for different user — not deleted")
        void deleteUnsentByUserIdAndSessionId_DifferentUser_NotDeleted() {
            Notification bobNotif = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .user(bob)
                    .session(session)
                    .build());
            em.clear();

            repository.deleteUnsentByUserIdAndSessionId(alice.getId(), session.getId());
            em.flush();

            assertThat(repository.findById(bobNotif.getId())).isPresent();
        }

        @Test
        @DisplayName("notification for different session — not deleted")
        void deleteUnsentByUserIdAndSessionId_DifferentSession_NotDeleted() {
            Notification otherSessionNotif = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .user(alice)
                    .session(otherSession)
                    .build());
            em.clear();

            repository.deleteUnsentByUserIdAndSessionId(alice.getId(), session.getId());
            em.flush();

            assertThat(repository.findById(otherSessionNotif.getId())).isPresent();
        }
    }

    // ---------------------------------------------------------------------------
    // deleteUnsentBySessionId (new — session cancel cleanup)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteUnsentBySessionId")
    class DeleteUnsentBySessionId {

        @Test
        @DisplayName("multiple unsent notifications for session — all deleted")
        void deleteUnsentBySessionId_MultipleUnsent_AllDeleted() {
            Notification n1 = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .user(alice)
                    .session(session)
                    .build());

            Notification n2 = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(7200))
                    .sent(false)
                    .user(bob)
                    .session(session)
                    .build());
            em.clear();

            repository.deleteUnsentBySessionId(session.getId());
            em.flush();

            assertThat(repository.findById(n1.getId())).isEmpty();
            assertThat(repository.findById(n2.getId())).isEmpty();
        }

        @Test
        @DisplayName("sent notifications for session — not deleted")
        void deleteUnsentBySessionId_SentNotification_NotDeleted() {
            Notification sent = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(50))
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            repository.deleteUnsentBySessionId(session.getId());
            em.flush();

            assertThat(repository.findById(sent.getId())).isPresent();
        }

        @Test
        @DisplayName("notifications for other session — not deleted")
        void deleteUnsentBySessionId_OtherSession_NotDeleted() {
            Notification otherNotif = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .user(alice)
                    .session(otherSession)
                    .build());
            em.clear();

            repository.deleteUnsentBySessionId(session.getId());
            em.flush();

            assertThat(repository.findById(otherNotif.getId())).isPresent();
        }
    }

    // ---------------------------------------------------------------------------
    // findByUserIdAndFilters (new — inbox with filters)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdAndFilters")
    class FindByUserIdAndFilters {

        @Test
        @DisplayName("sentOnly=true — only sent notifications returned")
        void findByUserIdAndFilters_SentOnly_ReturnsOnlySent() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(50))
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());

            // Unsent — should not appear
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            Page<Notification> result = repository.findByUserIdAndFilters(
                    alice.getId(), null, false, true, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1)
                    .allMatch(Notification::isSent);
        }

        @Test
        @DisplayName("unreadOnly=true — only unread notifications returned")
        void findByUserIdAndFilters_UnreadOnly_ReturnsOnlyUnread() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(50))
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().minusSeconds(120))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(100))
                    .read(true)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            Page<Notification> result = repository.findByUserIdAndFilters(
                    alice.getId(), null, true, false, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1)
                    .allMatch(n -> !n.isRead());
        }

        @Test
        @DisplayName("typeFilter=REMINDER — only REMINDER notifications returned")
        void findByUserIdAndFilters_TypeFilter_ReturnsOnlyMatchingType() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(50))
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(120))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(100))
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            Page<Notification> result = repository.findByUserIdAndFilters(
                    alice.getId(), NotificationType.REMINDER, false, false, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1)
                    .allMatch(n -> n.getType() == NotificationType.REMINDER);
        }

        @Test
        @DisplayName("no filters — all notifications for user returned")
        void findByUserIdAndFilters_NoFilters_ReturnsAll() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .sentAt(Instant.now().minusSeconds(50))
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now().plusSeconds(3600))
                    .sent(false)
                    .read(false)
                    .user(alice)
                    .session(session)
                    .build());
            em.clear();

            Page<Notification> result = repository.findByUserIdAndFilters(
                    alice.getId(), null, false, false, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("other user's notifications — not returned even with no filters")
        void findByUserIdAndFilters_OtherUser_NotReturned() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now().minusSeconds(60))
                    .sent(true)
                    .read(false)
                    .user(bob)
                    .session(session)
                    .build());
            em.clear();

            Page<Notification> result = repository.findByUserIdAndFilters(
                    alice.getId(), null, false, false, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // markAllReadByUserId (new — mark-all-read)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("markAllReadByUserId")
    class MarkAllReadByUserId {

        @Test
        @DisplayName("user with three unread — all marked read, count returned")
        void markAllReadByUserId_ThreeUnread_AllMarkedReadReturns3() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(alice)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(alice)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CANCELLATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(alice)
                    .build());

            em.clear();

            int updated = repository.markAllReadByUserId(alice.getId());

            assertThat(updated).isEqualTo(3);
            assertThat(repository.countByUserIdAndReadFalse(alice.getId())).isZero();
        }

        @Test
        @DisplayName("already-read notifications — count reflects only changed rows")
        void markAllReadByUserId_OneReadOneUnread_Returns1() {
            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(true)
                    .user(alice)
                    .build());

            em.persistAndFlush(Notification.builder()
                    .type(NotificationType.REMINDER)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(alice)
                    .build());

            em.clear();

            int updated = repository.markAllReadByUserId(alice.getId());

            assertThat(updated).isEqualTo(1);
        }

        @Test
        @DisplayName("other user's notifications — not marked read")
        void markAllReadByUserId_OtherUser_NotAffected() {
            Notification bobNotif = em.persistAndFlush(Notification.builder()
                    .type(NotificationType.CONFIRMATION)
                    .scheduledAt(Instant.now())
                    .sent(true)
                    .read(false)
                    .user(bob)
                    .build());
            em.clear();

            repository.markAllReadByUserId(alice.getId());
            em.flush();
            em.clear();

            Notification reloaded = repository.findById(bobNotif.getId()).orElseThrow();
            assertThat(reloaded.isRead()).isFalse();
        }
    }
}
