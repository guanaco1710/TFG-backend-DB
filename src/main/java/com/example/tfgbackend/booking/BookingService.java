package com.example.tfgbackend.booking;

import com.example.tfgbackend.booking.dto.BookingResponse;
import com.example.tfgbackend.booking.dto.ClassSessionSummary;
import com.example.tfgbackend.booking.dto.RosterEntryResponse;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.AlreadyBookedException;
import com.example.tfgbackend.common.exception.AlreadyOnWaitlistException;
import com.example.tfgbackend.common.exception.BookingAlreadyCancelledException;
import com.example.tfgbackend.common.exception.BookingNotFoundException;
import com.example.tfgbackend.common.exception.ClassFullException;
import com.example.tfgbackend.common.exception.MonthlyClassLimitReachedException;
import com.example.tfgbackend.common.exception.NoActiveSubscriptionException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.common.exception.WaitlistEntryNotFoundException;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.subscription.Subscription;
import com.example.tfgbackend.subscription.SubscriptionRepository;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import com.example.tfgbackend.waitlist.WaitlistEntry;
import com.example.tfgbackend.waitlist.WaitlistRepository;
import com.example.tfgbackend.waitlist.dto.WaitlistEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final WaitlistRepository waitlistRepository;
    private final SubscriptionRepository subscriptionRepository;

    // Optimistic locking on ClassSession.version prevents two concurrent bookings from
    // both seeing capacity available. The service also does a transactional count check
    // as a first guard before attempting the insert.
    @Transactional
    public BookingResponse createBooking(Long userId, Long sessionId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotBookableException(sessionId);
        }

        Optional<Booking> existing = bookingRepository.findByUserIdAndSessionId(userId, sessionId);
        if (existing.isPresent() && existing.get().getStatus() != BookingStatus.CANCELLED) {
            throw new AlreadyBookedException(userId, sessionId);
        }

        long confirmed = bookingRepository.countConfirmedBySessionId(sessionId);
        if (confirmed >= session.getMaxCapacity()) {
            throw new ClassFullException(sessionId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Class-limit enforcement: verify user has an active subscription with remaining classes
        Subscription sub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveSubscriptionException("No active subscription for user: " + userId));
        Integer cap = sub.getPlan().getClassesPerMonth();
        if (cap != null && sub.getClassesUsedThisMonth() >= cap) {
            throw new MonthlyClassLimitReachedException("Monthly class limit of " + cap + " reached");
        }
        sub.setClassesUsedThisMonth(sub.getClassesUsedThisMonth() + 1);
        // sub is a managed entity — the update is flushed automatically within this transaction

        Booking booking = Booking.builder()
                .user(user)
                .session(session)
                .status(BookingStatus.CONFIRMED)
                .bookedAt(Instant.now())
                .build();

        Booking saved = bookingRepository.save(booking);
        return toBookingResponse(saved);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long requestingUserId, Boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!isAdmin && !booking.getUser().getId().equals(requestingUserId)) {
            throw new BookingNotFoundException(bookingId);
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(bookingId);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Refund the class credit if the session hasn't started yet
        if (booking.getSession().getStartTime().isAfter(java.time.LocalDateTime.now())) {
            subscriptionRepository.findByUserIdAndStatus(booking.getUser().getId(), SubscriptionStatus.ACTIVE)
                    .ifPresent(s -> s.setClassesUsedThisMonth(Math.max(0, s.getClassesUsedThisMonth() - 1)));
        }

        // Promote the first waitlist entry to a CONFIRMED booking when a spot opens up
        List<WaitlistEntry> waitlist = waitlistRepository
                .findBySessionIdOrderByPositionAsc(booking.getSession().getId());
        if (!waitlist.isEmpty()) {
            WaitlistEntry first = waitlist.get(0);
            Booking promoted = Booking.builder()
                    .user(first.getUser())
                    .session(booking.getSession())
                    .status(BookingStatus.CONFIRMED)
                    .bookedAt(Instant.now())
                    .build();
            bookingRepository.save(promoted);
            waitlistRepository.delete(first);
        }
    }

    public PageResponse<BookingResponse> getMyBookings(Long userId, BookingStatus status, Pageable pageable) {
        Page<Booking> page;
        if (status == null) {
            page = bookingRepository.findByUserIdOrderByBookedAtDesc(userId, pageable);
        } else {
            page = bookingRepository.findByUserIdAndStatus(userId, status, pageable);
        }
        return PageResponse.of(page.map(this::toBookingResponse));
    }

    public BookingResponse getBookingById(Long bookingId, Long requestingUserId, Boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!isAdmin && !booking.getUser().getId().equals(requestingUserId)) {
            throw new BookingNotFoundException(bookingId);
        }

        return toBookingResponse(booking);
    }

    public PageResponse<RosterEntryResponse> getSessionRoster(Long sessionId, Pageable pageable) {
        classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        Page<Booking> page = bookingRepository.findBySessionId(sessionId, pageable);
        return PageResponse.of(page.map(b -> new RosterEntryResponse(
                b.getId(),
                b.getStatus(),
                b.getBookedAt(),
                b.getUser().getId(),
                b.getUser().getName(),
                b.getUser().getEmail()
        )));
    }

    @Transactional
    public WaitlistEntryResponse joinWaitlist(Long userId, Long sessionId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotBookableException(sessionId);
        }

        Optional<Booking> existingBooking = bookingRepository.findByUserIdAndSessionId(userId, sessionId);
        if (existingBooking.isPresent() && existingBooking.get().getStatus() != BookingStatus.CANCELLED) {
            throw new AlreadyBookedException(userId, sessionId);
        }

        waitlistRepository.findByUserIdAndSessionId(userId, sessionId).ifPresent(e -> {
            throw new AlreadyOnWaitlistException(userId, sessionId);
        });

        int position = (int) waitlistRepository.countBySessionId(sessionId) + 1;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        WaitlistEntry entry = WaitlistEntry.builder()
                .user(user)
                .session(session)
                .position(position)
                .joinedAt(Instant.now())
                .build();

        WaitlistEntry saved = waitlistRepository.save(entry);
        return toWaitlistResponse(saved);
    }

    @Transactional
    public void leaveWaitlist(Long userId, Long sessionId) {
        WaitlistEntry entry = waitlistRepository.findByUserIdAndSessionId(userId, sessionId)
                .orElseThrow(() -> new WaitlistEntryNotFoundException(userId, sessionId));
        waitlistRepository.delete(entry);
    }

    public List<WaitlistEntryResponse> getMyWaitlistEntries(Long userId) {
        return waitlistRepository.findByUserId(userId).stream()
                .map(this::toWaitlistResponse)
                .toList();
    }

    private BookingResponse toBookingResponse(Booking b) {
        ClassSession s = b.getSession();
        ClassSessionSummary summary = new ClassSessionSummary(
                s.getId(),
                s.getClassType().getName(),
                s.getStartTime(),
                s.getGym() != null ? s.getGym().getName() : null
        );
        return new BookingResponse(b.getId(), summary, b.getStatus(), b.getBookedAt());
    }

    private WaitlistEntryResponse toWaitlistResponse(WaitlistEntry e) {
        ClassSession s = e.getSession();
        ClassSessionSummary summary = new ClassSessionSummary(
                s.getId(),
                s.getClassType().getName(),
                s.getStartTime(),
                s.getGym() != null ? s.getGym().getName() : null
        );
        return new WaitlistEntryResponse(e.getId(), summary, e.getUser().getId(), e.getPosition(), e.getJoinedAt());
    }
}
