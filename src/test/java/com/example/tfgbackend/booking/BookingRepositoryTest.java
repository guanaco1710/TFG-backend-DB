package com.example.tfgbackend.booking;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRepositoryTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired BookingRepository repository;

    private User alice;
    private User bob;
    private ClassSession session1;
    private ClassSession session2;

    @BeforeEach
    void setUp() {
        ClassType spinning = em.persistAndFlush(ClassType.builder()
                .name("Spinning").description("Cycling class").level("INTERMEDIATE").build());

        session1 = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45).maxCapacity(12).room("1A")
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

    private Booking persistBooking(User user, ClassSession session, BookingStatus status, Instant bookedAt) {
        return em.persistAndFlush(Booking.builder()
                .user(user).session(session).status(status).bookedAt(bookedAt).build());
    }

    @Test
    void findByUserIdOrderByBookedAtDesc_MultipleBookings_ReturnsNewestFirst() {
        Instant older = Instant.now().minusSeconds(200);
        Instant newer = Instant.now().minusSeconds(100);
        persistBooking(alice, session1, BookingStatus.CONFIRMED, older);
        persistBooking(alice, session2, BookingStatus.CONFIRMED, newer);
        em.clear();

        Page<Booking> page = repository.findByUserIdOrderByBookedAtDesc(alice.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getBookedAt())
                .isAfter(page.getContent().get(1).getBookedAt());
    }

    @Test
    void findBySessionId_MultipleBookings_ReturnsAllForSession() {
        persistBooking(alice, session1, BookingStatus.CONFIRMED, Instant.now());
        persistBooking(bob, session1, BookingStatus.CONFIRMED, Instant.now());
        persistBooking(alice, session2, BookingStatus.CONFIRMED, Instant.now()); // different session
        em.clear();

        Page<Booking> page = repository.findBySessionId(session1.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2)
                .allMatch(b -> b.getSession().getId().equals(session1.getId()));
    }

    @Test
    void findByUserIdAndSessionId_ExistingBooking_ReturnsBooking() {
        persistBooking(alice, session1, BookingStatus.CONFIRMED, Instant.now());
        em.clear();

        Optional<Booking> found = repository.findByUserIdAndSessionId(alice.getId(), session1.getId());

        assertThat(found).isPresent();
    }

    @Test
    void findByUserIdAndSessionId_NoBooking_ReturnsEmpty() {
        Optional<Booking> found = repository.findByUserIdAndSessionId(alice.getId(), session1.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void countConfirmedBySessionId_MixedStatuses_ExcludesCancelled() {
        persistBooking(alice, session1, BookingStatus.CONFIRMED, Instant.now());
        persistBooking(bob, session1, BookingStatus.CANCELLED, Instant.now());
        em.clear();

        assertThat(repository.countConfirmedBySessionId(session1.getId())).isEqualTo(1);
    }

    @Test
    void countConfirmedBySessionId_AttendedAndNoShow_CountedAsOccupied() {
        persistBooking(alice, session1, BookingStatus.ATTENDED, Instant.now());
        persistBooking(bob, session1, BookingStatus.NO_SHOW, Instant.now());
        em.clear();

        assertThat(repository.countConfirmedBySessionId(session1.getId())).isEqualTo(2);
    }

    @Test
    void findByUserIdAndStatus_FilterByConfirmed_ReturnsOnlyConfirmed() {
        persistBooking(alice, session1, BookingStatus.CONFIRMED, Instant.now());
        persistBooking(alice, session2, BookingStatus.CANCELLED, Instant.now());
        em.clear();

        Page<Booking> page = repository.findByUserIdAndStatus(
                alice.getId(), BookingStatus.CONFIRMED, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1)
                .allMatch(b -> b.getStatus() == BookingStatus.CONFIRMED);
    }
}
