package com.example.tfgbackend.classsession;

import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.dto.ClassSessionRequest;
import com.example.tfgbackend.classsession.dto.ClassSessionResponse;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classtype.ClassTypeRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.common.exception.InstructorNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.gym.GymRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ClassSessionService}. No Spring context — all collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class ClassSessionServiceTest {

    @Mock ClassSessionRepository classSessionRepository;
    @Mock BookingRepository bookingRepository;
    @Mock ClassTypeRepository classTypeRepository;
    @Mock GymRepository gymRepository;
    @Mock UserRepository userRepository;
    @Mock com.example.tfgbackend.notification.NotificationService notificationService;

    @InjectMocks ClassSessionService classSessionService;

    private ClassType spinning;
    private Gym gym;
    private User instructor;
    private ClassSession scheduledSession;
    private ClassSession cancelledSession;

    @BeforeEach
    void setUp() {
        spinning = ClassType.builder().name("Spinning").description("Cycling class").level("INTERMEDIATE").build();
        setId(spinning, 10L);

        gym = Gym.builder().name("Downtown Gym").address("Main St 1").city("Madrid").active(true).build();
        setId(gym, 5L);

        instructor = User.builder().name("John Doe").email("johndoe@gym.com").passwordHash("hash")
                .role(UserRole.INSTRUCTOR).specialty("Spinning").build();
        setId(instructor, 3L);

        scheduledSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45)
                .maxCapacity(20)
                .room("A1")
                .status(SessionStatus.SCHEDULED)
                .classType(spinning)
                .gym(gym)
                .instructor(instructor)
                .build();
        setId(scheduledSession, 100L);

        cancelledSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(2))
                .durationMinutes(60)
                .maxCapacity(15)
                .room("B2")
                .status(SessionStatus.CANCELLED)
                .classType(spinning)
                .gym(gym)
                .instructor(null)
                .build();
        setId(cancelledSession, 101L);
    }

    // ---------------------------------------------------------------------------
    // createSession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createSession")
    class CreateSession {

        @Test
        @DisplayName("happy path: creates session and returns mapped response")
        void createSession_ValidRequest_ReturnsClassSessionResponse() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 5L, 3L,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(userRepository.findById(3L)).thenReturn(Optional.of(instructor));
            when(classSessionRepository.save(any())).thenAnswer(inv -> {
                ClassSession s = inv.getArgument(0);
                setId(s, 100L);
                return s;
            });
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);

            ClassSessionResponse response = classSessionService.createSession(req);

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.classType().id()).isEqualTo(10L);
            assertThat(response.classType().name()).isEqualTo("Spinning");
            assertThat(response.gym().id()).isEqualTo(5L);
            assertThat(response.instructor().id()).isEqualTo(3L);
            assertThat(response.maxCapacity()).isEqualTo(20);
            assertThat(response.confirmedCount()).isZero();
            assertThat(response.availableSpots()).isEqualTo(20);
            assertThat(response.status()).isEqualTo(SessionStatus.SCHEDULED);

            ArgumentCaptor<ClassSession> captor = ArgumentCaptor.forClass(ClassSession.class);
            verify(classSessionRepository).save(captor.capture());
            assertThat(captor.getValue().getClassType()).isEqualTo(spinning);
            assertThat(captor.getValue().getGym()).isEqualTo(gym);
            assertThat(captor.getValue().getInstructor()).isEqualTo(instructor);
        }

        @Test
        @DisplayName("classType not found throws ClassTypeNotFoundException")
        void createSession_ClassTypeNotFound_ThrowsClassTypeNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    999L, null, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classTypeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.createSession(req))
                    .isInstanceOf(ClassTypeNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("gym not found (when gymId provided) throws GymNotFoundException")
        void createSession_GymIdProvidedButNotFound_ThrowsGymNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 999L, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.createSession(req))
                    .isInstanceOf(GymNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("instructor not found (when instructorId provided) throws InstructorNotFoundException")
        void createSession_InstructorIdProvidedButNotFound_ThrowsInstructorNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 5L, 999L,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.createSession(req))
                    .isInstanceOf(InstructorNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("instructorId provided but user is not an instructor throws InstructorNotFoundException")
        void createSession_InstructorIdProvidedButUserNotInstructor_ThrowsInstructorNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 5L, 999L,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );
            User customer = User.builder().name("Alice").email("alice@test.com")
                    .passwordHash("hash").role(UserRole.CUSTOMER).build();

            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(userRepository.findById(999L)).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> classSessionService.createSession(req))
                    .isInstanceOf(InstructorNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("null gymId and null instructorId — saves session without gym or instructor")
        void createSession_NullGymAndInstructor_SavesWithoutGymOrInstructor() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, null, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(classSessionRepository.save(any())).thenAnswer(inv -> {
                ClassSession s = inv.getArgument(0);
                setId(s, 200L);
                return s;
            });
            when(bookingRepository.countConfirmedBySessionId(200L)).thenReturn(0L);

            ClassSessionResponse response = classSessionService.createSession(req);

            assertThat(response.gym()).isNull();
            assertThat(response.instructor()).isNull();

            ArgumentCaptor<ClassSession> captor = ArgumentCaptor.forClass(ClassSession.class);
            verify(classSessionRepository).save(captor.capture());
            assertThat(captor.getValue().getGym()).isNull();
            assertThat(captor.getValue().getInstructor()).isNull();
        }
    }

    // ---------------------------------------------------------------------------
    // getSessionById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getSessionById")
    class GetSessionById {

        @Test
        @DisplayName("happy path: returns mapped response for existing session")
        void getSessionById_SessionExists_ReturnsClassSessionResponse() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(3L);

            ClassSessionResponse response = classSessionService.getSessionById(100L);

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.classType().name()).isEqualTo("Spinning");
            assertThat(response.status()).isEqualTo(SessionStatus.SCHEDULED);
            assertThat(response.confirmedCount()).isEqualTo(3);
            assertThat(response.availableSpots()).isEqualTo(17);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void getSessionById_NotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.getSessionById(999L))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // getSchedule
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getSchedule")
    class GetSchedule {

        @Test
        @DisplayName("returns mapped list of sessions in range")
        void getSchedule_SessionsInRange_ReturnsMappedList() {
            LocalDateTime from = LocalDateTime.now();
            LocalDateTime to = from.plusDays(7);

            when(classSessionRepository.findSchedule(from, to, SessionStatus.SCHEDULED))
                    .thenReturn(List.of(scheduledSession));
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(2L);

            List<ClassSessionResponse> result = classSessionService.getSchedule(from, to);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(100L);
            assertThat(result.get(0).confirmedCount()).isEqualTo(2);
            assertThat(result.get(0).availableSpots()).isEqualTo(18);
        }

        @Test
        @DisplayName("returns empty list when no sessions in range")
        void getSchedule_NoSessions_ReturnsEmptyList() {
            LocalDateTime from = LocalDateTime.now();
            LocalDateTime to = from.plusDays(7);

            when(classSessionRepository.findSchedule(from, to, SessionStatus.SCHEDULED))
                    .thenReturn(List.of());

            List<ClassSessionResponse> result = classSessionService.getSchedule(from, to);

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // listSessions
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("listSessions")
    class ListSessions {

        @Test
        @DisplayName("with classTypeId filter delegates to findByClassTypeId")
        void listSessions_WithClassTypeId_UsesFilteredQuery() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ClassSession> page = new PageImpl<>(List.of(scheduledSession), pageable, 1);

            when(classSessionRepository.findByClassTypeId(10L, pageable)).thenReturn(page);
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);

            PageResponse<ClassSessionResponse> result = classSessionService.listSessions(10L, pageable);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).classType().id()).isEqualTo(10L);
            assertThat(result.totalElements()).isEqualTo(1);
            verify(classSessionRepository).findByClassTypeId(10L, pageable);
            verify(classSessionRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("without classTypeId filter uses findAll(pageable)")
        void listSessions_WithoutClassTypeId_UsesAllQuery() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ClassSession> page = new PageImpl<>(List.of(scheduledSession), pageable, 1);

            when(classSessionRepository.findAll(pageable)).thenReturn(page);
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);

            PageResponse<ClassSessionResponse> result = classSessionService.listSessions(null, pageable);

            assertThat(result.content()).hasSize(1);
            verify(classSessionRepository).findAll(pageable);
            verify(classSessionRepository, never()).findByClassTypeId(any(), any());
        }

        @Test
        @DisplayName("empty page returns page with no content")
        void listSessions_EmptyPage_ReturnsEmptyPageResponse() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ClassSession> empty = Page.empty(pageable);

            when(classSessionRepository.findAll(pageable)).thenReturn(empty);

            PageResponse<ClassSessionResponse> result = classSessionService.listSessions(null, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // updateSession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("updateSession")
    class UpdateSession {

        @Test
        @DisplayName("happy path: updates all fields and returns updated response")
        void updateSession_ValidRequest_ReturnsUpdatedResponse() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 5L, 3L,
                    LocalDateTime.now().plusDays(3),
                    90, 30, "C3"
            );

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(userRepository.findById(3L)).thenReturn(Optional.of(instructor));
            when(classSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(5L);

            ClassSessionResponse response = classSessionService.updateSession(100L, req);

            assertThat(response.durationMinutes()).isEqualTo(90);
            assertThat(response.maxCapacity()).isEqualTo(30);
            assertThat(response.room()).isEqualTo("C3");
            assertThat(response.confirmedCount()).isEqualTo(5);
            assertThat(response.availableSpots()).isEqualTo(25);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void updateSession_SessionNotFound_ThrowsSessionNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, null, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.updateSession(999L, req))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("updating CANCELLED session throws SessionNotBookableException")
        void updateSession_CancelledSession_ThrowsSessionNotBookableException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, null, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classSessionRepository.findById(101L)).thenReturn(Optional.of(cancelledSession));

            assertThatThrownBy(() -> classSessionService.updateSession(101L, req))
                    .isInstanceOf(SessionNotBookableException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("classType not found on update throws ClassTypeNotFoundException")
        void updateSession_ClassTypeNotFound_ThrowsClassTypeNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    999L, null, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classTypeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.updateSession(100L, req))
                    .isInstanceOf(ClassTypeNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("null gymId and null instructorId on update — saves session without gym or instructor")
        void updateSession_NullGymAndInstructor_SavesWithoutGymOrInstructor() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, null, null,
                    LocalDateTime.now().plusDays(3),
                    60, 25, "D4"
            );

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(classSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);

            ClassSessionResponse response = classSessionService.updateSession(100L, req);

            assertThat(response.gym()).isNull();
            assertThat(response.instructor()).isNull();
        }

        @Test
        @DisplayName("gym not found (when gymId provided) on update throws GymNotFoundException")
        void updateSession_GymIdProvidedButNotFound_ThrowsGymNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 999L, null,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.updateSession(100L, req))
                    .isInstanceOf(GymNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("instructorId provided but user is not an instructor on update throws InstructorNotFoundException")
        void updateSession_InstructorIdProvidedButUserNotInstructor_ThrowsInstructorNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 5L, 999L,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );
            User customer = User.builder().name("Alice").email("alice@test.com")
                    .passwordHash("hash").role(UserRole.CUSTOMER).build();

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(userRepository.findById(999L)).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> classSessionService.updateSession(100L, req))
                    .isInstanceOf(InstructorNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("instructor not found (when instructorId provided) on update throws InstructorNotFoundException")
        void updateSession_InstructorIdProvidedButNotFound_ThrowsInstructorNotFoundException() {
            ClassSessionRequest req = new ClassSessionRequest(
                    10L, 5L, 999L,
                    LocalDateTime.now().plusDays(1),
                    45, 20, "A1"
            );

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classTypeRepository.findById(10L)).thenReturn(Optional.of(spinning));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.updateSession(100L, req))
                    .isInstanceOf(InstructorNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // cancelSession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("cancelSession")
    class CancelSession {

        @Test
        @DisplayName("happy path: sets status to CANCELLED")
        void cancelSession_ScheduledSession_SetsStatusToCancelled() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(classSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            classSessionService.cancelSession(100L);

            assertThat(scheduledSession.getStatus()).isEqualTo(SessionStatus.CANCELLED);
            verify(classSessionRepository).save(scheduledSession);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void cancelSession_NotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classSessionService.cancelSession(999L))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(classSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("already CANCELLED session throws SessionNotBookableException")
        void cancelSession_AlreadyCancelled_ThrowsSessionNotBookableException() {
            when(classSessionRepository.findById(101L)).thenReturn(Optional.of(cancelledSession));

            assertThatThrownBy(() -> classSessionService.cancelSession(101L))
                    .isInstanceOf(SessionNotBookableException.class);

            verify(classSessionRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

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
