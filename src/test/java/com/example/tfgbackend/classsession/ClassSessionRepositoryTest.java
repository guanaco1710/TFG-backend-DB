package com.example.tfgbackend.classsession;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassSessionRepositoryTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired ClassSessionRepository repository;

    private ClassType spinning;
    private ClassType yoga;
    private User instructor;

    @BeforeEach
    void setUp() {
        spinning = em.persistAndFlush(ClassType.builder()
                .name("Spinning").description("Cycling").level("INTERMEDIATE").build());
        yoga = em.persistAndFlush(ClassType.builder()
                .name("Yoga").description("Flexibility").level("BASIC").build());
        instructor = em.persistAndFlush(User.builder()
                .name("Jorge").email("jorge@gym.com").passwordHash("hash")
                .role(UserRole.INSTRUCTOR).specialty("Spinning").build());
    }

    private ClassSession persistSession(ClassType classType, User instr,
                                        OffsetDateTime start, SessionStatus status) {
        return em.persistAndFlush(ClassSession.builder()
                .classType(classType).instructor(instr)
                .startTime(start).durationMinutes(45).maxCapacity(12).room("1A")
                .status(status).build());
    }

    @Test
    void findSchedule_SessionsInWindow_ReturnsSortedByStartTime() {
        OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        persistSession(spinning, instructor, windowStart.plusHours(2), SessionStatus.SCHEDULED);
        persistSession(yoga, null, windowStart.plusHours(1), SessionStatus.SCHEDULED);
        persistSession(spinning, null, windowStart.minusDays(5), SessionStatus.SCHEDULED); // outside window
        em.clear();

        List<ClassSession> schedule = repository.findSchedule(
                windowStart, windowStart.plusDays(1), SessionStatus.SCHEDULED);

        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).getStartTime()).isBefore(schedule.get(1).getStartTime());
    }

    @Test
    void findSchedule_NoSessionsInWindow_ReturnsEmpty() {
        OffsetDateTime pastWindow = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20);
        List<ClassSession> schedule = repository.findSchedule(
                pastWindow, pastWindow.plusDays(1), SessionStatus.SCHEDULED);
        assertThat(schedule).isEmpty();
    }

    @Test
    void findSchedule_WrongStatus_ExcludesNonMatchingStatus() {
        OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        persistSession(spinning, null, windowStart.plusHours(1), SessionStatus.CANCELLED);
        em.clear();

        List<ClassSession> schedule = repository.findSchedule(
                windowStart, windowStart.plusDays(1), SessionStatus.SCHEDULED);

        assertThat(schedule).isEmpty();
    }

    @Test
    void findByClassTypeId_MatchingType_ReturnsPaged() {
        persistSession(spinning, null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), SessionStatus.SCHEDULED);
        persistSession(spinning, null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(2), SessionStatus.SCHEDULED);
        persistSession(yoga, null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), SessionStatus.SCHEDULED);
        em.clear();

        Page<ClassSession> page = repository.findByClassTypeId(spinning.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2)
                .allMatch(s -> s.getClassType().getId().equals(spinning.getId()));
    }

    @Test
    void findByInstructorId_MatchingInstructor_ReturnsPaged() {
        persistSession(spinning, instructor, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), SessionStatus.SCHEDULED);
        persistSession(yoga, null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(2), SessionStatus.SCHEDULED);
        em.clear();

        Page<ClassSession> page = repository.findByInstructorId(instructor.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1)
                .allMatch(s -> s.getInstructor().getId().equals(instructor.getId()));
    }

    @Test
    void countByClassTypeIdAndStatusAndStartTimeAfter_FutureSessions_ReturnsCorrectCount() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        persistSession(spinning, null, now.plusDays(1), SessionStatus.SCHEDULED);
        persistSession(spinning, null, now.plusDays(2), SessionStatus.SCHEDULED);
        persistSession(spinning, null, now.minusDays(1), SessionStatus.FINISHED); // past, wrong status
        persistSession(yoga, null, now.plusDays(1), SessionStatus.SCHEDULED);    // wrong class type
        em.clear();

        long count = repository.countByClassTypeIdAndStatusAndStartTimeAfter(
                spinning.getId(), SessionStatus.SCHEDULED, now);

        assertThat(count).isEqualTo(2);
    }
}
