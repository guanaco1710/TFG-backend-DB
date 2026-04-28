package com.example.tfgbackend.stats;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.booking.Booking;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests for the stats-specific query methods added to
 * {@link BookingRepository}.
 *
 * Written before the query methods exist (TDD).
 */
class BookingRepositoryStatsTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired BookingRepository repository;

    private User alice;
    private User bob;
    private ClassSession session1;
    private ClassSession session2;

    @BeforeEach
    void setUp() {
        ClassType spinning = em.persistAndFlush(ClassType.builder()
                .name("Spinning").description("Cycling").level("INTERMEDIATE").build());

        session1 = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45).maxCapacity(10).room("1A")
                .classType(spinning).build());

        session2 = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(2))
                .durationMinutes(60).maxCapacity(10).room("2B")
                .classType(spinning).build());

        alice = em.persistAndFlush(User.builder()
                .name("Alice").email("alice@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());

        bob = em.persistAndFlush(User.builder()
                .name("Bob").email("bob@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());
    }

    private Booking persist(User user, ClassSession session, BookingStatus status, Instant bookedAt) {
        return em.persistAndFlush(Booking.builder()
                .user(user).session(session).status(status).bookedAt(bookedAt).build());
    }

    // -------------------------------------------------------------------------
    // countByUserId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("countByUserId returns total bookings for the user regardless of status")
    void countByUserId_MultipleStatuses_ReturnsTotal() {
        persist(alice, session1, BookingStatus.CONFIRMED, Instant.now());
        persist(alice, session2, BookingStatus.CANCELLED, Instant.now());
        persist(bob, session1, BookingStatus.CONFIRMED, Instant.now());
        em.clear();

        long count = repository.countByUserId(alice.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByUserId returns 0 when user has no bookings")
    void countByUserId_NoBookings_ReturnsZero() {
        em.clear();

        long count = repository.countByUserId(alice.getId());

        assertThat(count).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // countByUserIdAndStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("countByUserIdAndStatus_CANCELLED counts only cancelled bookings for the user")
    void countByUserIdAndStatus_Cancelled_ReturnsOnlyCancelled() {
        persist(alice, session1, BookingStatus.CONFIRMED, Instant.now());
        persist(alice, session2, BookingStatus.CANCELLED, Instant.now());
        persist(bob, session1, BookingStatus.CANCELLED, Instant.now());
        em.clear();

        long count = repository.countByUserIdAndStatus(alice.getId(), BookingStatus.CANCELLED);

        assertThat(count).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // countByUserIdAndBookedAtBetween
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("countByUserIdAndBookedAtBetween counts bookings within the given range")
    void countByUserIdAndBookedAtBetween_WithinRange_ReturnsCount() {
        YearMonth currentMonth = YearMonth.now();
        Instant start = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        // Inside range
        persist(alice, session1, BookingStatus.CONFIRMED, start.plusSeconds(3600));
        // Outside range (before)
        persist(alice, session2, BookingStatus.CONFIRMED, start.minusSeconds(1));
        em.clear();

        long count = repository.countByUserIdAndBookedAtBetween(alice.getId(), start, end);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("countByUserIdAndBookedAtBetween excludes other users' bookings")
    void countByUserIdAndBookedAtBetween_OtherUser_ExcludesThem() {
        YearMonth currentMonth = YearMonth.now();
        Instant start = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        persist(alice, session1, BookingStatus.CONFIRMED, start.plusSeconds(3600));
        persist(bob, session2, BookingStatus.CONFIRMED, start.plusSeconds(7200));
        em.clear();

        long count = repository.countByUserIdAndBookedAtBetween(alice.getId(), start, end);

        assertThat(count).isEqualTo(1);
    }
}
