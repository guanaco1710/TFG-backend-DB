package com.example.tfgbackend.notification;

import com.example.tfgbackend.booking.Booking;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.NotificationType;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.notification.dto.MarkReadResponse;
import com.example.tfgbackend.notification.dto.NotificationResponse;
import com.example.tfgbackend.notification.dto.UnreadCountResponse;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.waitlist.WaitlistEntry;
import com.example.tfgbackend.waitlist.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link NotificationService}.
 *
 * No Spring context — all collaborators are mocked with Mockito.
 * This test suite is written ahead of the implementation (TDD).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock BookingRepository bookingRepository;
    @Mock WaitlistRepository waitlistRepository;
    @Mock ClassSessionRepository classSessionRepository;

    @InjectMocks NotificationService notificationService;

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    private User alice;
    private User bob;
    private ClassType spinning;
    private ClassSession futureSession;
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        alice = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER);
        bob   = buildUser(2L, "Bob",   "bob@test.com",   UserRole.CUSTOMER);

        spinning = ClassType.builder()
                .name("Spinning")
                .description("High-intensity cycling")
                .level("INTERMEDIATE")
                .build();
        setId(spinning, 10L);

        // Session starts in 25 hours → REMINDER should be at sessionStart - 24h
        futureSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusHours(25))
                .durationMinutes(45)
                .maxCapacity(10)
                .room("1A")
                .status(SessionStatus.SCHEDULED)
                .classType(spinning)
                .build();
        setId(futureSession, 100L);

        confirmedBooking = buildBooking(200L, alice, futureSession, BookingStatus.CONFIRMED);
    }

    // ---------------------------------------------------------------------------
    // createBookingConfirmed
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createBookingConfirmed")
    class CreateBookingConfirmed {

        @Test
        @DisplayName("creates a CONFIRMATION and a REMINDER notification")
        void createBookingConfirmed_ValidBooking_CreatesTwoNotifications() {
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> {
                        Notification n = inv.getArgument(0);
                        setId(n, 1L);
                        return n;
                    });

            notificationService.createBookingConfirmed(confirmedBooking);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            List<Notification> saved = captor.getAllValues();
            assertThat(saved).extracting(Notification::getType)
                    .containsExactlyInAnyOrder(NotificationType.CONFIRMATION, NotificationType.REMINDER);

            // Both notifications belong to alice
            assertThat(saved).allMatch(n -> n.getUser().equals(alice));

            // Both reference the session
            assertThat(saved).allMatch(n -> n.getSession().equals(futureSession));
        }

        @Test
        @DisplayName("REMINDER scheduledAt = sessionStart - 24h when session is far in future")
        void createBookingConfirmed_FarFutureSession_ReminderScheduledAt24HBefore() {
            // Session 48 hours from now → reminder at 48-24 = 24h from now
            ClassSession farSession = ClassSession.builder()
                    .startTime(LocalDateTime.now().plusHours(48))
                    .durationMinutes(45)
                    .maxCapacity(10)
                    .room("2B")
                    .status(SessionStatus.SCHEDULED)
                    .classType(spinning)
                    .build();
            setId(farSession, 101L);

            Booking bookingFar = buildBooking(201L, alice, farSession, BookingStatus.CONFIRMED);

            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.createBookingConfirmed(bookingFar);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            Notification reminder = captor.getAllValues().stream()
                    .filter(n -> n.getType() == NotificationType.REMINDER)
                    .findFirst()
                    .orElseThrow();

            // scheduledAt should be approximately sessionStart - 24h
            // Far session starts in 48h; reminder at 48-24 = 24h from now
            Instant expectedReminderAt = farSession.getStartTime()
                    .minusHours(24)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant();
            assertThat(reminder.getScheduledAt())
                    .isCloseTo(expectedReminderAt, within(5, java.time.temporal.ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("REMINDER scheduledAt = now when session is very soon (< 24h away)")
        void createBookingConfirmed_ImmineentSession_ReminderScheduledAtNow() {
            // Session starts in 2 hours → sessionStart - 24h is in the past → use now
            ClassSession soonSession = ClassSession.builder()
                    .startTime(LocalDateTime.now().plusHours(2))
                    .durationMinutes(45)
                    .maxCapacity(10)
                    .room("3C")
                    .status(SessionStatus.SCHEDULED)
                    .classType(spinning)
                    .build();
            setId(soonSession, 102L);

            Booking bookingSoon = buildBooking(202L, alice, soonSession, BookingStatus.CONFIRMED);

            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            notificationService.createBookingConfirmed(bookingSoon);
            Instant after = Instant.now();

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            Notification reminder = captor.getAllValues().stream()
                    .filter(n -> n.getType() == NotificationType.REMINDER)
                    .findFirst()
                    .orElseThrow();

            // scheduledAt should be max(now, sessionStart-24h) ≈ now
            assertThat(reminder.getScheduledAt())
                    .isBetween(before, after.plusSeconds(2));
        }

        @Test
        @DisplayName("new notifications are created with sent=false and read=false")
        void createBookingConfirmed_NewNotifications_SentFalseAndReadFalse() {
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.createBookingConfirmed(confirmedBooking);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            assertThat(captor.getAllValues()).allMatch(n -> !n.isSent());
            assertThat(captor.getAllValues()).allMatch(n -> !n.isRead());
        }
    }

    // ---------------------------------------------------------------------------
    // createBookingCancelled
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createBookingCancelled")
    class CreateBookingCancelled {

        @Test
        @DisplayName("creates a CANCELLATION notification and deletes unsent REMINDER")
        void createBookingCancelled_ValidArgs_CreatesCancellationAndDeletesReminder() {
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.createBookingCancelled(alice.getId(), futureSession);

            ArgumentCaptor<Notification> saveCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(saveCaptor.capture());

            Notification cancellation = saveCaptor.getValue();
            assertThat(cancellation.getType()).isEqualTo(NotificationType.CANCELLATION);
            assertThat(cancellation.getUser()).isEqualTo(alice);
            assertThat(cancellation.getSession()).isEqualTo(futureSession);
            assertThat(cancellation.isSent()).isFalse();

            // Unsent REMINDER for this user+session must be deleted
            verify(notificationRepository)
                    .deleteUnsentByUserIdAndSessionId(alice.getId(), futureSession.getId());
        }

        @Test
        @DisplayName("CANCELLATION notification is created with sent=false and read=false")
        void createBookingCancelled_NewCancellation_SentFalseAndReadFalse() {
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.createBookingCancelled(alice.getId(), futureSession);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            assertThat(captor.getValue().isSent()).isFalse();
            assertThat(captor.getValue().isRead()).isFalse();
        }
    }

    // ---------------------------------------------------------------------------
    // createSessionCancelled
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createSessionCancelled")
    class CreateSessionCancelled {

        @Test
        @DisplayName("all CONFIRMED bookings → each user gets a CANCELLATION notification")
        void createSessionCancelled_ConfirmedBookings_EachUserGetsCancellation() {
            Booking booking1 = buildBooking(201L, alice, futureSession, BookingStatus.CONFIRMED);
            Booking booking2 = buildBooking(202L, bob,   futureSession, BookingStatus.CONFIRMED);

            when(bookingRepository.findBySessionIdAndStatusIn(
                    eq(futureSession.getId()), any()))
                    .thenReturn(List.of(booking1, booking2));
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(futureSession.getId()))
                    .thenReturn(List.of());
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.createSessionCancelled(futureSession);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            assertThat(captor.getAllValues())
                    .allMatch(n -> n.getType() == NotificationType.CANCELLATION);
            assertThat(captor.getAllValues())
                    .extracting(n -> n.getUser().getId())
                    .containsExactlyInAnyOrder(alice.getId(), bob.getId());
        }

        @Test
        @DisplayName("WAITLISTED users also receive CANCELLATION notifications")
        void createSessionCancelled_WaitlistedUsers_AlsoGetCancellation() {
            User carol = buildUser(3L, "Carol", "carol@test.com", UserRole.CUSTOMER);
            Booking confirmedB = buildBooking(201L, alice, futureSession, BookingStatus.CONFIRMED);
            WaitlistEntry wlEntry = buildWaitlistEntry(10L, carol, futureSession, 1);

            when(bookingRepository.findBySessionIdAndStatusIn(
                    eq(futureSession.getId()), any()))
                    .thenReturn(List.of(confirmedB));
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(futureSession.getId()))
                    .thenReturn(List.of(wlEntry));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            notificationService.createSessionCancelled(futureSession);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            assertThat(captor.getAllValues())
                    .extracting(n -> n.getUser().getId())
                    .containsExactlyInAnyOrder(alice.getId(), carol.getId());
        }

        @Test
        @DisplayName("unsent notifications for the session are deleted for all users")
        void createSessionCancelled_UnsentNotifications_AllDeleted() {
            when(bookingRepository.findBySessionIdAndStatusIn(
                    eq(futureSession.getId()), any()))
                    .thenReturn(List.of());
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(futureSession.getId()))
                    .thenReturn(List.of());

            notificationService.createSessionCancelled(futureSession);

            verify(notificationRepository).deleteUnsentBySessionId(futureSession.getId());
        }

        @Test
        @DisplayName("session with no bookings or waitlist — only cleanup, no notifications saved")
        void createSessionCancelled_NoBookingsOrWaitlist_NoNotificationsSaved() {
            when(bookingRepository.findBySessionIdAndStatusIn(
                    eq(futureSession.getId()), any()))
                    .thenReturn(List.of());
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(futureSession.getId()))
                    .thenReturn(List.of());

            notificationService.createSessionCancelled(futureSession);

            verify(notificationRepository, never()).save(any());
            verify(notificationRepository).deleteUnsentBySessionId(futureSession.getId());
        }
    }

    // ---------------------------------------------------------------------------
    // getMyNotifications
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getMyNotifications")
    class GetMyNotifications {

        @Test
        @DisplayName("sentOnly=true default — only sent notifications returned in inbox")
        void getMyNotifications_SentOnlyTrue_ReturnsOnlySent() {
            Notification sent = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            Page<Notification> page = new PageImpl<>(
                    List.of(sent), PageRequest.of(0, 20), 1);

            when(notificationRepository.findByUserIdAndFilters(
                    eq(alice.getId()), eq(null), eq(false), eq(true), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<NotificationResponse> result = notificationService.getMyNotifications(
                    alice.getId(), null, false, true, PageRequest.of(0, 20));

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
            verify(notificationRepository).findByUserIdAndFilters(
                    eq(alice.getId()), eq(null), eq(false), eq(true), any(Pageable.class));
        }

        @Test
        @DisplayName("typeFilter forwarded to repository")
        void getMyNotifications_TypeFilter_ForwardedToRepository() {
            Page<Notification> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(notificationRepository.findByUserIdAndFilters(
                    eq(alice.getId()), eq(NotificationType.REMINDER),
                    eq(false), eq(true), any(Pageable.class)))
                    .thenReturn(page);

            notificationService.getMyNotifications(
                    alice.getId(), NotificationType.REMINDER, false, true, PageRequest.of(0, 20));

            verify(notificationRepository).findByUserIdAndFilters(
                    eq(alice.getId()), eq(NotificationType.REMINDER),
                    eq(false), eq(true), any(Pageable.class));
        }

        @Test
        @DisplayName("unreadOnly=true — forwarded to repository")
        void getMyNotifications_UnreadOnly_ForwardedToRepository() {
            Page<Notification> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(notificationRepository.findByUserIdAndFilters(
                    eq(alice.getId()), eq(null), eq(true), eq(false), any(Pageable.class)))
                    .thenReturn(page);

            notificationService.getMyNotifications(
                    alice.getId(), null, true, false, PageRequest.of(0, 20));

            verify(notificationRepository).findByUserIdAndFilters(
                    eq(alice.getId()), eq(null), eq(true), eq(false), any(Pageable.class));
        }

        @Test
        @DisplayName("response DTO fields are correctly mapped")
        void getMyNotifications_ResponseMapped_FieldsCorrect() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            Page<Notification> page = new PageImpl<>(
                    List.of(n), PageRequest.of(0, 20), 1);

            when(notificationRepository.findByUserIdAndFilters(any(), any(), any(), any(), any()))
                    .thenReturn(page);

            PageResponse<NotificationResponse> result = notificationService.getMyNotifications(
                    alice.getId(), null, false, true, PageRequest.of(0, 20));

            NotificationResponse dto = result.content().get(0);
            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.type()).isEqualTo(NotificationType.CONFIRMATION);
            assertThat(dto.sent()).isTrue();
            assertThat(dto.userId()).isEqualTo(alice.getId());
            assertThat(dto.sessionId()).isEqualTo(futureSession.getId());
        }
    }

    // ---------------------------------------------------------------------------
    // getUnreadCount
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCount {

        @Test
        @DisplayName("returns count from repository as UnreadCountResponse")
        void getUnreadCount_UserWithUnread_ReturnsCount() {
            when(notificationRepository.countByUserIdAndReadFalse(alice.getId()))
                    .thenReturn(5L);

            UnreadCountResponse result = notificationService.getUnreadCount(alice.getId());

            assertThat(result.unread()).isEqualTo(5L);
        }

        @Test
        @DisplayName("user with no unread — returns 0")
        void getUnreadCount_NoUnread_ReturnsZero() {
            when(notificationRepository.countByUserIdAndReadFalse(alice.getId()))
                    .thenReturn(0L);

            UnreadCountResponse result = notificationService.getUnreadCount(alice.getId());

            assertThat(result.unread()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // getById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("owner retrieves own notification — returns response")
        void getById_OwnerRequests_ReturnsResponse() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            NotificationResponse result = notificationService.getById(1L, alice.getId(), false);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.userId()).isEqualTo(alice.getId());
        }

        @Test
        @DisplayName("admin retrieves any notification — returns response")
        void getById_AdminRequests_ReturnsResponse() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            NotificationResponse result = notificationService.getById(1L, 99L, true);

            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("non-owner, non-admin — throws NotificationNotFoundException (IDOR protection)")
        void getById_NonOwnerNonAdmin_ThrowsNotificationNotFoundException() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            // callerId=99 is not alice and not admin
            assertThatThrownBy(() -> notificationService.getById(1L, 99L, false))
                    .isInstanceOf(com.example.tfgbackend.common.exception.NotificationNotFoundException.class)
                    .hasMessageContaining("1");
        }

        @Test
        @DisplayName("notification not found — throws NotificationNotFoundException")
        void getById_NotFound_ThrowsNotificationNotFoundException() {
            when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getById(999L, alice.getId(), false))
                    .isInstanceOf(com.example.tfgbackend.common.exception.NotificationNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ---------------------------------------------------------------------------
    // markAsRead
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("owner marks unread notification — updated=1 and notification.read=true")
        void markAsRead_UnreadOwnerNotification_Returns1AndSetsRead() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MarkReadResponse result = notificationService.markAsRead(1L, alice.getId(), false);

            assertThat(result.updated()).isEqualTo(1);
            verify(notificationRepository).save(n);
            assertThat(n.isRead()).isTrue();
        }

        @Test
        @DisplayName("already-read notification — idempotent, returns updated=0")
        void markAsRead_AlreadyRead_Idempotent_Returns0() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, true); // already read
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            MarkReadResponse result = notificationService.markAsRead(1L, alice.getId(), false);

            assertThat(result.updated()).isZero();
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("admin marks any notification — succeeds")
        void markAsRead_Admin_MarksAnyNotification() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            MarkReadResponse result = notificationService.markAsRead(1L, 99L, true);

            assertThat(result.updated()).isEqualTo(1);
        }

        @Test
        @DisplayName("non-owner, non-admin — throws NotificationNotFoundException (IDOR)")
        void markAsRead_NonOwnerNonAdmin_ThrowsNotificationNotFoundException() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            assertThatThrownBy(() -> notificationService.markAsRead(1L, 99L, false))
                    .isInstanceOf(com.example.tfgbackend.common.exception.NotificationNotFoundException.class);

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("notification not found — throws NotificationNotFoundException")
        void markAsRead_NotFound_ThrowsNotificationNotFoundException() {
            when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999L, alice.getId(), false))
                    .isInstanceOf(com.example.tfgbackend.common.exception.NotificationNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // markAllAsRead
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("markAllAsRead")
    class MarkAllAsRead {

        @Test
        @DisplayName("marks all unread for user — returns count from repository")
        void markAllAsRead_UserWithUnread_ReturnsUpdatedCount() {
            when(notificationRepository.markAllReadByUserId(alice.getId())).thenReturn(3);

            MarkReadResponse result = notificationService.markAllAsRead(alice.getId());

            assertThat(result.updated()).isEqualTo(3);
            verify(notificationRepository).markAllReadByUserId(alice.getId());
        }

        @Test
        @DisplayName("user with no unread — returns 0")
        void markAllAsRead_NoneUnread_Returns0() {
            when(notificationRepository.markAllReadByUserId(alice.getId())).thenReturn(0);

            MarkReadResponse result = notificationService.markAllAsRead(alice.getId());

            assertThat(result.updated()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("owner deletes own notification — repository.delete called")
        void delete_OwnerDeletes_DeleteCalled() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            notificationService.delete(1L, alice.getId(), false);

            verify(notificationRepository).delete(n);
        }

        @Test
        @DisplayName("admin deletes any notification — repository.delete called")
        void delete_AdminDeletes_DeleteCalled() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            notificationService.delete(1L, 99L, true);

            verify(notificationRepository).delete(n);
        }

        @Test
        @DisplayName("non-owner, non-admin — throws NotificationNotFoundException (IDOR, returns 404)")
        void delete_NonOwnerNonAdmin_ThrowsNotificationNotFoundException() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            assertThatThrownBy(() -> notificationService.delete(1L, 99L, false))
                    .isInstanceOf(com.example.tfgbackend.common.exception.NotificationNotFoundException.class)
                    .hasMessageContaining("1");

            verify(notificationRepository, never()).delete(any(Notification.class));
        }

        @Test
        @DisplayName("notification not found — throws NotificationNotFoundException")
        void delete_NotFound_ThrowsNotificationNotFoundException() {
            when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.delete(999L, alice.getId(), false))
                    .isInstanceOf(com.example.tfgbackend.common.exception.NotificationNotFoundException.class);

            verify(notificationRepository, never()).delete(any(Notification.class));
        }
    }

    // ---------------------------------------------------------------------------
    // getUserNotifications (admin view)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getUserNotifications (admin)")
    class GetUserNotifications {

        @Test
        @DisplayName("no type filter — returns all notifications for user paginated")
        void getUserNotifications_NoFilter_ReturnsAllPaged() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            Page<Notification> page = new PageImpl<>(
                    List.of(n), PageRequest.of(0, 20), 1);

            when(notificationRepository.findByUserIdAndFilters(
                    eq(alice.getId()), eq(null), eq(false), eq(false), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<NotificationResponse> result = notificationService
                    .getUserNotifications(alice.getId(), null, PageRequest.of(0, 20));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).userId()).isEqualTo(alice.getId());
        }

        @Test
        @DisplayName("typeFilter=CANCELLATION — forwarded to repository")
        void getUserNotifications_TypeFilter_ForwardedToRepository() {
            Page<Notification> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
            when(notificationRepository.findByUserIdAndFilters(
                    eq(alice.getId()), eq(NotificationType.CANCELLATION),
                    eq(false), eq(false), any(Pageable.class)))
                    .thenReturn(page);

            notificationService.getUserNotifications(
                    alice.getId(), NotificationType.CANCELLATION, PageRequest.of(0, 20));

            verify(notificationRepository).findByUserIdAndFilters(
                    eq(alice.getId()), eq(NotificationType.CANCELLATION),
                    eq(false), eq(false), any(Pageable.class));
        }
    }

    // ---------------------------------------------------------------------------
    // getSessionNotifications (admin/instructor view)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getSessionNotifications")
    class GetSessionNotifications {

        @Test
        @DisplayName("session exists — returns list of NotificationResponse")
        void getSessionNotifications_SessionExists_ReturnsList() {
            Notification n = buildNotification(1L, alice, futureSession,
                    NotificationType.CONFIRMATION, true, false);
            when(classSessionRepository.existsById(futureSession.getId())).thenReturn(true);
            when(notificationRepository.findBySessionIdOrderByScheduledAtDesc(futureSession.getId()))
                    .thenReturn(List.of(n));

            List<NotificationResponse> result =
                    notificationService.getSessionNotifications(futureSession.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).sessionId()).isEqualTo(futureSession.getId());
        }

        @Test
        @DisplayName("session not found — throws SessionNotFoundException")
        void getSessionNotifications_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> notificationService.getSessionNotifications(999L))
                    .isInstanceOf(SessionNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ---------------------------------------------------------------------------
    // dispatchPending
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("dispatchPending")
    class DispatchPending {

        @Test
        @DisplayName("pending due notifications — sent=true and sentAt set for each")
        void dispatchPending_PendingNotifications_MarkedSentWithSentAt() {
            Notification n1 = buildNotification(1L, alice, futureSession,
                    NotificationType.REMINDER, false, false);
            Notification n2 = buildNotification(2L, bob, futureSession,
                    NotificationType.CONFIRMATION, false, false);

            when(notificationRepository.findPendingDue(any(Instant.class)))
                    .thenReturn(List.of(n1, n2));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            notificationService.dispatchPending();
            Instant after = Instant.now().plusSeconds(2);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            List<Notification> saved = captor.getAllValues();
            assertThat(saved).allMatch(Notification::isSent);
            assertThat(saved).allMatch(n -> n.getSentAt() != null);
            assertThat(saved.get(0).getSentAt()).isBetween(before, after);
            assertThat(saved.get(1).getSentAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("no pending notifications — nothing saved")
        void dispatchPending_NoPendingNotifications_NothingSaved() {
            when(notificationRepository.findPendingDue(any(Instant.class)))
                    .thenReturn(List.of());

            notificationService.dispatchPending();

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("already-sent notifications are not returned by findPendingDue — not double-processed")
        void dispatchPending_AlreadySentNotInPending_NotDoubleProcessed() {
            // findPendingDue only returns unsent → simulate already-sent not returned
            when(notificationRepository.findPendingDue(any(Instant.class)))
                    .thenReturn(List.of()); // already-sent excluded by query

            notificationService.dispatchPending();

            verify(notificationRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private User buildUser(Long id, String name, String email, UserRole role) {
        User u = User.builder()
                .name(name).email(email)
                .passwordHash("$2a$12$hash").role(role).active(true).build();
        setId(u, id);
        return u;
    }

    private Booking buildBooking(Long id, User user, ClassSession session, BookingStatus status) {
        Booking b = Booking.builder()
                .user(user).session(session).status(status)
                .bookedAt(Instant.now()).build();
        setId(b, id);
        return b;
    }

    private WaitlistEntry buildWaitlistEntry(Long id, User user, ClassSession session, int position) {
        WaitlistEntry e = WaitlistEntry.builder()
                .user(user).session(session).position(position)
                .joinedAt(Instant.now()).build();
        setId(e, id);
        return e;
    }

    private Notification buildNotification(Long id, User user, ClassSession session,
                                           NotificationType type, boolean sent, boolean read) {
        Notification n = Notification.builder()
                .type(type)
                .scheduledAt(sent ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(3600))
                .sent(sent)
                .sentAt(sent ? Instant.now().minusSeconds(50) : null)
                .read(read)
                .user(user)
                .session(session)
                .build();
        setId(n, id);
        return n;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
