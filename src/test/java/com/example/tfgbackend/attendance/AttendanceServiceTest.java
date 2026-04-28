package com.example.tfgbackend.attendance;

import com.example.tfgbackend.attendance.dto.AttendanceEntryRequest;
import com.example.tfgbackend.attendance.dto.AttendanceResponse;
import com.example.tfgbackend.attendance.dto.RecordAttendanceRequest;
import com.example.tfgbackend.booking.Booking;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.common.exception.AttendanceNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotAttendableException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.AttendanceStatus;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AttendanceService}. No Spring context — all collaborators are mocked.
 *
 * <p>Written in the TDD red phase; the service methods throw
 * {@link UnsupportedOperationException} until the green phase is complete.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock AttendanceRepository attendanceRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock UserRepository userRepository;
    @Mock BookingRepository bookingRepository;

    @InjectMocks AttendanceService attendanceService;

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    private User instructor;
    private User customer;
    private ClassType spinning;
    private ClassSession finishedSession;
    private ClassSession activeSession;
    private ClassSession scheduledSession;
    private ClassSession cancelledSession;

    @BeforeEach
    void setUp() {
        instructor = buildUser(10L, "Instructor Bob", "bob@test.com", UserRole.INSTRUCTOR);
        customer   = buildUser(1L,  "Alice",          "alice@test.com", UserRole.CUSTOMER);

        spinning = ClassType.builder()
                .name("Spinning").description("Cycling class").level("INTERMEDIATE").build();
        setId(spinning, 10L);

        finishedSession = ClassSession.builder()
                .startTime(LocalDateTime.now().minusDays(1))
                .durationMinutes(45).maxCapacity(10).room("1A")
                .status(SessionStatus.FINISHED)
                .classType(spinning).build();
        setId(finishedSession, 100L);

        activeSession = ClassSession.builder()
                .startTime(LocalDateTime.now())
                .durationMinutes(45).maxCapacity(10).room("1B")
                .status(SessionStatus.ACTIVE)
                .classType(spinning).build();
        setId(activeSession, 101L);

        scheduledSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45).maxCapacity(10).room("2A")
                .status(SessionStatus.SCHEDULED)
                .classType(spinning).build();
        setId(scheduledSession, 102L);

        cancelledSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(2))
                .durationMinutes(45).maxCapacity(10).room("2B")
                .status(SessionStatus.CANCELLED)
                .classType(spinning).build();
        setId(cancelledSession, 103L);
    }

    // ---------------------------------------------------------------------------
    // recordAttendance
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("recordAttendance")
    class RecordAttendance {

        @Test
        @DisplayName("happy path: creates new attendance records for a FINISHED session and returns responses")
        void recordAttendance_FinishedSession_CreatesAndReturnsResponses() {
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(attendanceRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenAnswer(inv -> {
                Attendance a = inv.getArgument(0);
                setId(a, 200L);
                return a;
            });

            List<AttendanceResponse> responses = attendanceService.recordAttendance(100L, request);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).id()).isEqualTo(200L);
            assertThat(responses.get(0).userId()).isEqualTo(1L);
            assertThat(responses.get(0).sessionId()).isEqualTo(100L);
            assertThat(responses.get(0).status()).isEqualTo(AttendanceStatus.ATTENDED);

            ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
            verify(attendanceRepository).save(captor.capture());
            Attendance saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(customer);
            assertThat(saved.getSession()).isEqualTo(finishedSession);
            assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
            assertThat(saved.getBooking()).isNull();
        }

        @Test
        @DisplayName("happy path: creates new attendance record for an ACTIVE session")
        void recordAttendance_ActiveSession_CreatesRecord() {
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(101L)).thenReturn(Optional.of(activeSession));
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(attendanceRepository.findByUserIdAndSessionId(1L, 101L)).thenReturn(Optional.empty());
            when(bookingRepository.findByUserIdAndSessionId(1L, 101L)).thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenAnswer(inv -> {
                Attendance a = inv.getArgument(0);
                setId(a, 201L);
                return a;
            });

            List<AttendanceResponse> responses = attendanceService.recordAttendance(101L, request);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).id()).isEqualTo(201L);
        }

        @Test
        @DisplayName("links existing Booking when one is found for the user+session")
        void recordAttendance_ExistingBooking_LinksBookingToAttendance() {
            Booking booking = buildBooking(50L, customer, finishedSession, BookingStatus.CONFIRMED);
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(attendanceRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(booking));
            when(attendanceRepository.save(any())).thenAnswer(inv -> {
                Attendance a = inv.getArgument(0);
                setId(a, 202L);
                return a;
            });

            attendanceService.recordAttendance(100L, request);

            ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
            verify(attendanceRepository).save(captor.capture());
            assertThat(captor.getValue().getBooking()).isEqualTo(booking);
        }

        @Test
        @DisplayName("upsert: updates status on existing attendance record instead of inserting a new one")
        void recordAttendance_ExistingRecord_UpdatesStatusInPlace() {
            Attendance existing = buildAttendance(200L, customer, finishedSession, null, AttendanceStatus.NO_SHOW);
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(attendanceRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(existing));
            when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<AttendanceResponse> responses = attendanceService.recordAttendance(100L, request);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).status()).isEqualTo(AttendanceStatus.ATTENDED);

            ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
            verify(attendanceRepository).save(captor.capture());
            // Must be the same object (mutated), not a brand-new instance
            assertThat(captor.getValue()).isSameAs(existing);
            assertThat(captor.getValue().getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        }

        @Test
        @DisplayName("bulk: processes multiple entries in a single request")
        void recordAttendance_MultipleEntries_SavesAll() {
            User bob = buildUser(2L, "Bob", "bob@test.com", UserRole.CUSTOMER);
            RecordAttendanceRequest request = new RecordAttendanceRequest(List.of(
                    new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED),
                    new AttendanceEntryRequest(2L, AttendanceStatus.NO_SHOW)));

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(attendanceRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(attendanceRepository.findByUserIdAndSessionId(2L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.findByUserIdAndSessionId(2L, 100L)).thenReturn(Optional.empty());
            when(attendanceRepository.save(any())).thenAnswer(inv -> {
                Attendance a = inv.getArgument(0);
                setId(a, System.nanoTime());
                return a;
            });

            List<AttendanceResponse> responses = attendanceService.recordAttendance(100L, request);

            assertThat(responses).hasSize(2);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void recordAttendance_SessionNotFound_ThrowsSessionNotFoundException() {
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.recordAttendance(999L, request))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("SCHEDULED session throws SessionNotAttendableException")
        void recordAttendance_ScheduledSession_ThrowsSessionNotAttendableException() {
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(102L)).thenReturn(Optional.of(scheduledSession));

            assertThatThrownBy(() -> attendanceService.recordAttendance(102L, request))
                    .isInstanceOf(SessionNotAttendableException.class);

            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("CANCELLED session throws SessionNotAttendableException")
        void recordAttendance_CancelledSession_ThrowsSessionNotAttendableException() {
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(103L)).thenReturn(Optional.of(cancelledSession));

            assertThatThrownBy(() -> attendanceService.recordAttendance(103L, request))
                    .isInstanceOf(SessionNotAttendableException.class);

            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("user not found throws UserNotFoundException on first missing entry")
        void recordAttendance_UserNotFound_ThrowsUserNotFoundException() {
            RecordAttendanceRequest request = new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(999L, AttendanceStatus.ATTENDED)));

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.recordAttendance(100L, request))
                    .isInstanceOf(UserNotFoundException.class);

            verify(attendanceRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // getSessionAttendance
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getSessionAttendance")
    class GetSessionAttendance {

        @Test
        @DisplayName("happy path: returns all attendance records for the session")
        void getSessionAttendance_ValidSession_ReturnsAllRecords() {
            Attendance a1 = buildAttendance(200L, customer, finishedSession, null, AttendanceStatus.ATTENDED);
            User bob = buildUser(2L, "Bob", "bob@test.com", UserRole.CUSTOMER);
            Attendance a2 = buildAttendance(201L, bob, finishedSession, null, AttendanceStatus.NO_SHOW);

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(attendanceRepository.findBySessionId(100L)).thenReturn(List.of(a1, a2));

            List<AttendanceResponse> responses = attendanceService.getSessionAttendance(100L);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).id()).isEqualTo(200L);
            assertThat(responses.get(0).userId()).isEqualTo(1L);
            assertThat(responses.get(0).status()).isEqualTo(AttendanceStatus.ATTENDED);
            assertThat(responses.get(1).id()).isEqualTo(201L);
            assertThat(responses.get(1).userId()).isEqualTo(2L);
            assertThat(responses.get(1).status()).isEqualTo(AttendanceStatus.NO_SHOW);
        }

        @Test
        @DisplayName("returns empty list when no attendance has been recorded yet")
        void getSessionAttendance_NoRecords_ReturnsEmptyList() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(finishedSession));
            when(attendanceRepository.findBySessionId(100L)).thenReturn(List.of());

            List<AttendanceResponse> responses = attendanceService.getSessionAttendance(100L);

            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void getSessionAttendance_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.getSessionAttendance(999L))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(attendanceRepository, never()).findBySessionId(any());
        }
    }

    // ---------------------------------------------------------------------------
    // deleteAttendance
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteAttendance")
    class DeleteAttendance {

        @Test
        @DisplayName("happy path: deletes attendance record that belongs to the given session")
        void deleteAttendance_ValidRecord_DeletesSuccessfully() {
            Attendance a = buildAttendance(200L, customer, finishedSession, null, AttendanceStatus.ATTENDED);

            when(attendanceRepository.findById(200L)).thenReturn(Optional.of(a));

            attendanceService.deleteAttendance(100L, 200L);

            verify(attendanceRepository).delete(a);
        }

        @Test
        @DisplayName("attendance record not found throws AttendanceNotFoundException")
        void deleteAttendance_RecordNotFound_ThrowsAttendanceNotFoundException() {
            when(attendanceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.deleteAttendance(100L, 999L))
                    .isInstanceOf(AttendanceNotFoundException.class);

            verify(attendanceRepository, never()).delete(any());
        }

        @Test
        @DisplayName("attendance record belongs to a different session throws AttendanceNotFoundException")
        void deleteAttendance_RecordBelongsToDifferentSession_ThrowsAttendanceNotFoundException() {
            // The attendance record references session 101 (activeSession), but caller passes sessionId=100
            Attendance a = buildAttendance(200L, customer, activeSession, null, AttendanceStatus.ATTENDED);

            when(attendanceRepository.findById(200L)).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> attendanceService.deleteAttendance(100L, 200L))
                    .isInstanceOf(AttendanceNotFoundException.class);

            verify(attendanceRepository, never()).delete(any());
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

    private Attendance buildAttendance(Long id, User user, ClassSession session,
                                       Booking booking, AttendanceStatus status) {
        Attendance a = Attendance.builder()
                .user(user).session(session).booking(booking)
                .status(status).recordedAt(Instant.now()).build();
        setId(a, id);
        return a;
    }

    /**
     * Reflectively sets the {@code id} field on a {@link com.example.tfgbackend.common.BaseEntity}
     * subclass. Needed because the field is private and has no setter.
     */
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
