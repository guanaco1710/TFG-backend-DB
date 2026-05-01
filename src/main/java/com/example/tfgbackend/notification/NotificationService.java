package com.example.tfgbackend.notification;

import com.example.tfgbackend.booking.Booking;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.NotificationNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.NotificationType;
import com.example.tfgbackend.notification.dto.MarkReadResponse;
import com.example.tfgbackend.notification.dto.NotificationResponse;
import com.example.tfgbackend.notification.dto.UnreadCountResponse;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import com.example.tfgbackend.waitlist.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BookingRepository bookingRepository;
    private final WaitlistRepository waitlistRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createBookingConfirmed(Booking booking) {
        User user = booking.getUser();
        ClassSession session = booking.getSession();

        notificationRepository.save(Notification.builder()
                .type(NotificationType.CONFIRMATION)
                .scheduledAt(Instant.now())
                .user(user)
                .session(session)
                .build());

        Instant sessionStart = session.getStartTime()
                .atZone(ZoneId.systemDefault())
                .toInstant();
        Instant reminderAt = sessionStart.minusSeconds(24 * 3600);
        if (reminderAt.isBefore(Instant.now())) {
            reminderAt = Instant.now();
        }

        notificationRepository.save(Notification.builder()
                .type(NotificationType.REMINDER)
                .scheduledAt(reminderAt)
                .user(user)
                .session(session)
                .build());
    }

    @Transactional
    public void createBookingCancelled(Long userId, ClassSession session) {
        User user = userRepository.getReferenceById(userId);

        notificationRepository.deleteUnsentByUserIdAndSessionId(userId, session.getId());

        notificationRepository.save(Notification.builder()
                .type(NotificationType.CANCELLATION)
                .scheduledAt(Instant.now())
                .user(user)
                .session(session)
                .build());
    }

    @Transactional
    public void createSessionCancelled(ClassSession session) {
        notificationRepository.deleteUnsentBySessionId(session.getId());

        List<User> bookedUsers = bookingRepository
                .findBySessionIdAndStatusIn(session.getId(),
                        List.of(BookingStatus.CONFIRMED, BookingStatus.WAITLISTED))
                .stream()
                .map(Booking::getUser)
                .toList();

        Set<Long> seen = bookedUsers.stream().map(User::getId).collect(Collectors.toSet());

        List<User> waitlistUsers = waitlistRepository
                .findBySessionIdOrderByPositionAsc(session.getId())
                .stream()
                .map(e -> e.getUser())
                .filter(u -> seen.add(u.getId()))
                .toList();

        for (User user : bookedUsers) {
            notificationRepository.save(Notification.builder()
                    .type(NotificationType.CANCELLATION)
                    .scheduledAt(Instant.now())
                    .user(user)
                    .session(session)
                    .build());
        }
        for (User user : waitlistUsers) {
            notificationRepository.save(Notification.builder()
                    .type(NotificationType.CANCELLATION)
                    .scheduledAt(Instant.now())
                    .user(user)
                    .session(session)
                    .build());
        }
    }

    public PageResponse<NotificationResponse> getMyNotifications(
            Long userId, NotificationType type, boolean unreadOnly, boolean sentOnly, Pageable pageable) {
        return PageResponse.of(
                notificationRepository.findByUserIdAndFilters(userId, type, unreadOnly, sentOnly, pageable)
                        .map(this::toResponse));
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
    }

    public NotificationResponse getById(Long id, Long callerId, boolean isAdmin) {
        Notification n = loadAndCheckAccess(id, callerId, isAdmin);
        return toResponse(n);
    }

    @Transactional
    public MarkReadResponse markAsRead(Long id, Long callerId, boolean isAdmin) {
        Notification n = loadAndCheckAccess(id, callerId, isAdmin);
        if (n.isRead()) {
            return new MarkReadResponse(0);
        }
        n.setRead(true);
        notificationRepository.save(n);
        return new MarkReadResponse(1);
    }

    @Transactional
    public MarkReadResponse markAllAsRead(Long userId) {
        return new MarkReadResponse(notificationRepository.markAllReadByUserId(userId));
    }

    @Transactional
    public void delete(Long id, Long callerId, boolean isAdmin) {
        Notification n = loadAndCheckAccess(id, callerId, isAdmin);
        notificationRepository.delete(n);
    }

    public PageResponse<NotificationResponse> getUserNotifications(
            Long userId, NotificationType type, Pageable pageable) {
        return PageResponse.of(
                notificationRepository.findByUserIdAndFilters(userId, type, false, false, pageable)
                        .map(this::toResponse));
    }

    public List<NotificationResponse> getSessionNotifications(Long sessionId) {
        if (!classSessionRepository.existsById(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        return notificationRepository.findBySessionIdOrderByScheduledAtDesc(sessionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void dispatchPending() {
        List<Notification> due = notificationRepository.findPendingDue(Instant.now());
        Instant now = Instant.now();
        for (Notification n : due) {
            n.setSent(true);
            n.setSentAt(now);
            notificationRepository.save(n);
        }
    }

    private Notification loadAndCheckAccess(Long id, Long callerId, boolean isAdmin) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (!isAdmin && !n.getUser().getId().equals(callerId)) {
            throw new NotificationNotFoundException(id);
        }
        return n;
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getScheduledAt(),
                n.isSent(),
                n.getSentAt(),
                n.isRead(),
                n.getUser().getId(),
                n.getSession() != null ? n.getSession().getId() : null);
    }
}
