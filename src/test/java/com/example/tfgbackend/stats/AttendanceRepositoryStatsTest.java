package com.example.tfgbackend.stats;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.attendance.Attendance;
import com.example.tfgbackend.attendance.AttendanceRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.enums.AttendanceStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests verifying the new query methods added to
 * {@link AttendanceRepository} for the stats feature.
 *
 * Written before the query methods are added (TDD).
 */
class AttendanceRepositoryStatsTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired AttendanceRepository repository;

    private User alice;
    private User bob;
    private ClassType spinning;
    private ClassType yoga;
    private ClassSession session1;
    private ClassSession session2;
    private ClassSession session3;

    @BeforeEach
    void setUp() {
        spinning = em.persistAndFlush(ClassType.builder()
                .name("Spinning").description("Cycling").level("INTERMEDIATE").build());

        yoga = em.persistAndFlush(ClassType.builder()
                .name("Yoga").description("Yoga class").level("BASIC").build());

        session1 = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().minusDays(3))
                .durationMinutes(45).maxCapacity(10).room("1A")
                .classType(spinning).build());

        session2 = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().minusDays(2))
                .durationMinutes(60).maxCapacity(10).room("2B")
                .classType(spinning).build());

        session3 = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().minusDays(1))
                .durationMinutes(30).maxCapacity(10).room("3C")
                .classType(yoga).build());

        alice = em.persistAndFlush(User.builder()
                .name("Alice").email("alice@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());

        bob = em.persistAndFlush(User.builder()
                .name("Bob").email("bob@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());
    }

    private Attendance persist(User user, ClassSession sess, AttendanceStatus status, Instant recordedAt) {
        return em.persistAndFlush(Attendance.builder()
                .user(user).session(sess).status(status).recordedAt(recordedAt).build());
    }

    // -------------------------------------------------------------------------
    // countByUserIdAndStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("countByUserIdAndStatus_ATTENDED counts only attended records for the user")
    void countByUserIdAndStatus_Attended_ReturnsCorrectCount() {
        persist(alice, session1, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(300));
        persist(alice, session2, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(200));
        persist(alice, session3, AttendanceStatus.NO_SHOW, Instant.now().minusSeconds(100));
        persist(bob, session1, AttendanceStatus.ATTENDED, Instant.now());
        em.clear();

        long count = repository.countByUserIdAndStatus(alice.getId(), AttendanceStatus.ATTENDED);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByUserIdAndStatus_NO_SHOW counts only no-show records for the user")
    void countByUserIdAndStatus_NoShow_ReturnsCorrectCount() {
        persist(alice, session1, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(200));
        persist(alice, session2, AttendanceStatus.NO_SHOW, Instant.now().minusSeconds(100));
        em.clear();

        long count = repository.countByUserIdAndStatus(alice.getId(), AttendanceStatus.NO_SHOW);

        assertThat(count).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // findAllByUserIdAndStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAllByUserIdAndStatus returns all attended records for the user")
    void findAllByUserIdAndStatus_Attended_ReturnsOnlyAttended() {
        persist(alice, session1, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(200));
        persist(alice, session2, AttendanceStatus.NO_SHOW, Instant.now().minusSeconds(100));
        persist(bob, session3, AttendanceStatus.ATTENDED, Instant.now());
        em.clear();

        List<Attendance> result = repository.findAllByUserIdAndStatus(alice.getId(), AttendanceStatus.ATTENDED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(result.get(0).getUser().getId()).isEqualTo(alice.getId());
    }

    // -------------------------------------------------------------------------
    // findByUserIdOrderByRecordedAtDesc (pageable)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByUserIdOrderByRecordedAtDesc returns records newest first")
    void findByUserIdOrderByRecordedAtDesc_MultipleRecords_ReturnsNewestFirst() {
        Instant older = Instant.now().minusSeconds(500);
        Instant newer = Instant.now().minusSeconds(100);
        persist(alice, session1, AttendanceStatus.ATTENDED, older);
        persist(alice, session2, AttendanceStatus.NO_SHOW, newer);
        em.clear();

        Page<Attendance> page = repository.findByUserIdOrderByRecordedAtDesc(
                alice.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getRecordedAt())
                .isAfter(page.getContent().get(1).getRecordedAt());
    }

    @Test
    @DisplayName("findByUserIdOrderByRecordedAtDesc does not return other user records")
    void findByUserIdOrderByRecordedAtDesc_OtherUserRecords_ExcludesThem() {
        persist(alice, session1, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(100));
        persist(bob, session2, AttendanceStatus.ATTENDED, Instant.now());
        em.clear();

        Page<Attendance> page = repository.findByUserIdOrderByRecordedAtDesc(
                alice.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1)
                .allMatch(a -> a.getUser().getId().equals(alice.getId()));
    }

    @Test
    @DisplayName("findByUserIdOrderByRecordedAtDesc fetches session and classType eagerly (no LazyInit)")
    void findByUserIdOrderByRecordedAtDesc_FetchesSessionAndClassType_NoLazyInitException() {
        persist(alice, session1, AttendanceStatus.ATTENDED, Instant.now());
        em.clear();

        Page<Attendance> page = repository.findByUserIdOrderByRecordedAtDesc(
                alice.getId(), PageRequest.of(0, 10));

        Attendance a = page.getContent().get(0);
        // Accessing these would throw LazyInitializationException if not fetched
        assertThat(a.getSession().getRoom()).isEqualTo("1A");
        assertThat(a.getSession().getClassType().getName()).isEqualTo("Spinning");
    }

    // -------------------------------------------------------------------------
    // findFavoriteClassTypeByUserId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findFavoriteClassTypeByUserId returns the class type with most ATTENDED records")
    void findFavoriteClassTypeByUserId_SpinningMoreThanYoga_ReturnsSpinning() {
        persist(alice, session1, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(300));
        persist(alice, session2, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(200));
        persist(alice, session3, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(100));
        // Spinning has 2, Yoga has 1
        em.clear();

        Optional<String> favorite = repository.findFavoriteClassTypeByUserId(alice.getId());

        assertThat(favorite).isPresent().hasValue("Spinning");
    }

    @Test
    @DisplayName("findFavoriteClassTypeByUserId excludes NO_SHOW records from the count")
    void findFavoriteClassTypeByUserId_NoShowRecordsIgnored_ReturnsCorrectFavorite() {
        // Yoga has 1 ATTENDED, Spinning has 2 NO_SHOW — Yoga should win
        persist(alice, session3, AttendanceStatus.ATTENDED, Instant.now().minusSeconds(200));
        persist(alice, session1, AttendanceStatus.NO_SHOW, Instant.now().minusSeconds(100));
        persist(alice, session2, AttendanceStatus.NO_SHOW, Instant.now());
        em.clear();

        Optional<String> favorite = repository.findFavoriteClassTypeByUserId(alice.getId());

        assertThat(favorite).isPresent().hasValue("Yoga");
    }

    @Test
    @DisplayName("findFavoriteClassTypeByUserId returns empty when user has no attended sessions")
    void findFavoriteClassTypeByUserId_NoAttendedSessions_ReturnsEmpty() {
        persist(alice, session1, AttendanceStatus.NO_SHOW, Instant.now());
        em.clear();

        Optional<String> favorite = repository.findFavoriteClassTypeByUserId(alice.getId());

        assertThat(favorite).isEmpty();
    }
}
