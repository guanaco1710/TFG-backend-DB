package com.example.tfgbackend.rating;

import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.NotAttendedSessionException;
import com.example.tfgbackend.common.exception.RatingAlreadyExistsException;
import com.example.tfgbackend.common.exception.RatingNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.rating.dto.CreateRatingRequest;
import com.example.tfgbackend.rating.dto.RatingResponse;
import com.example.tfgbackend.rating.dto.UpdateRatingRequest;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    RatingRepository ratingRepository;

    @Mock
    ClassSessionRepository classSessionRepository;

    @Mock
    BookingRepository bookingRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    RatingService ratingService;

    private ClassSession session;
    private User customer;
    private Rating rating;

    @BeforeEach
    void setUp() {
        session = ClassSession.builder()
                .startTime(java.time.LocalDateTime.now().minusDays(1))
                .durationMinutes(45)
                .maxCapacity(12)
                .room("1A")
                .build();
        setId(session, 10L);

        customer = User.builder()
                .name("Alice")
                .email("alice@test.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .build();
        setId(customer, 2L);

        rating = Rating.builder()
                .score(4)
                .comment("Great class")
                .user(customer)
                .session(session)
                .build();
        setId(rating, 1L);
    }

    // ---------------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        private final CreateRatingRequest validRequest =
                new CreateRatingRequest(10L, 4, "Great class");

        @Test
        @DisplayName("valid request, user has ATTENDED booking — saves and returns RatingResponse")
        void create_ValidRequestAttendedBooking_SavesAndReturnsResponse() {
            when(classSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(bookingRepository.existsByUserIdAndSessionIdAndStatus(
                    2L, 10L, BookingStatus.ATTENDED)).thenReturn(true);
            when(ratingRepository.findByUserIdAndSessionId(2L, 10L)).thenReturn(Optional.empty());
            when(userRepository.findById(2L)).thenReturn(Optional.of(customer));
            when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> {
                Rating saved = inv.getArgument(0);
                setId(saved, 1L);
                return saved;
            });

            RatingResponse response = ratingService.create(2L, validRequest);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.score()).isEqualTo(4);
            assertThat(response.comment()).isEqualTo("Great class");
            assertThat(response.userId()).isEqualTo(2L);
            assertThat(response.sessionId()).isEqualTo(10L);

            ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
            verify(ratingRepository).save(captor.capture());
            assertThat(captor.getValue().getScore()).isEqualTo(4);
            assertThat(captor.getValue().getComment()).isEqualTo("Great class");
            assertThat(captor.getValue().getUser()).isEqualTo(customer);
            assertThat(captor.getValue().getSession()).isEqualTo(session);
        }

        @Test
        @DisplayName("session not found — throws SessionNotFoundException")
        void create_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.create(2L, validRequest))
                    .isInstanceOf(SessionNotFoundException.class)
                    .hasMessageContaining("10");

            verify(ratingRepository, never()).save(any());
        }

        @Test
        @DisplayName("user has no ATTENDED booking — throws NotAttendedSessionException")
        void create_NoAttendedBooking_ThrowsNotAttendedSessionException() {
            when(classSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(bookingRepository.existsByUserIdAndSessionIdAndStatus(
                    2L, 10L, BookingStatus.ATTENDED)).thenReturn(false);

            assertThatThrownBy(() -> ratingService.create(2L, validRequest))
                    .isInstanceOf(NotAttendedSessionException.class)
                    .hasMessageContaining("2")
                    .hasMessageContaining("10");

            verify(ratingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rating already exists for user and session — throws RatingAlreadyExistsException")
        void create_DuplicateRating_ThrowsRatingAlreadyExistsException() {
            when(classSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(bookingRepository.existsByUserIdAndSessionIdAndStatus(
                    2L, 10L, BookingStatus.ATTENDED)).thenReturn(true);
            when(ratingRepository.findByUserIdAndSessionId(2L, 10L)).thenReturn(Optional.of(rating));

            assertThatThrownBy(() -> ratingService.create(2L, validRequest))
                    .isInstanceOf(RatingAlreadyExistsException.class)
                    .hasMessageContaining("2")
                    .hasMessageContaining("10");

            verify(ratingRepository, never()).save(any());
        }

        @Test
        @DisplayName("user not found after validation — throws UserNotFoundException")
        void create_UserNotFound_ThrowsUserNotFoundException() {
            when(classSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(bookingRepository.existsByUserIdAndSessionIdAndStatus(
                    2L, 10L, BookingStatus.ATTENDED)).thenReturn(true);
            when(ratingRepository.findByUserIdAndSessionId(2L, 10L)).thenReturn(Optional.empty());
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.create(2L, validRequest))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("2");

            verify(ratingRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // update
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("update")
    class Update {

        private final UpdateRatingRequest updateRequest = new UpdateRatingRequest(5, "Excellent!");

        @Test
        @DisplayName("caller owns the rating — updates score, comment, ratedAt and returns response")
        void update_CallerOwnsRating_UpdatesAndReturnsResponse() {
            when(ratingRepository.findById(1L)).thenReturn(Optional.of(rating));
            when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

            RatingResponse response = ratingService.update(2L, 1L, updateRequest);

            assertThat(response.score()).isEqualTo(5);
            assertThat(response.comment()).isEqualTo("Excellent!");
            assertThat(response.userId()).isEqualTo(2L);
            assertThat(response.sessionId()).isEqualTo(10L);

            // ratedAt was updated — just verify it's not null (timing-sensitive)
            assertThat(response.ratedAt()).isNotNull();

            verify(ratingRepository).save(rating);
        }

        @Test
        @DisplayName("rating not found — throws RatingNotFoundException")
        void update_RatingNotFound_ThrowsRatingNotFoundException() {
            when(ratingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.update(2L, 999L, updateRequest))
                    .isInstanceOf(RatingNotFoundException.class)
                    .hasMessageContaining("999");

            verify(ratingRepository, never()).save(any());
        }

        @Test
        @DisplayName("caller is not the owner — throws RatingNotFoundException")
        void update_CallerNotOwner_ThrowsRatingNotFoundException() {
            // Rating belongs to user 2L, but caller is user 99L
            when(ratingRepository.findById(1L)).thenReturn(Optional.of(rating));

            assertThatThrownBy(() -> ratingService.update(99L, 1L, updateRequest))
                    .isInstanceOf(RatingNotFoundException.class)
                    .hasMessageContaining("1");

            verify(ratingRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("ADMIN deletes any rating — calls repository.delete")
        void delete_AdminDeletesAnyRating_DeletesCalled() {
            when(ratingRepository.findById(1L)).thenReturn(Optional.of(rating));

            // Admin (user 99L) deleting a rating owned by user 2L
            ratingService.delete(99L, UserRole.ADMIN, 1L);

            verify(ratingRepository).delete(rating);
        }

        @Test
        @DisplayName("CUSTOMER deletes own rating — calls repository.delete")
        void delete_CustomerDeletesOwnRating_DeletesCalled() {
            when(ratingRepository.findById(1L)).thenReturn(Optional.of(rating));

            ratingService.delete(2L, UserRole.CUSTOMER, 1L);

            verify(ratingRepository).delete(rating);
        }

        @Test
        @DisplayName("rating not found — throws RatingNotFoundException")
        void delete_RatingNotFound_ThrowsRatingNotFoundException() {
            when(ratingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.delete(2L, UserRole.CUSTOMER, 999L))
                    .isInstanceOf(RatingNotFoundException.class)
                    .hasMessageContaining("999");

            verify(ratingRepository, never()).delete(any(Rating.class));
        }

        @Test
        @DisplayName("CUSTOMER tries to delete someone else's rating — throws RatingNotFoundException")
        void delete_CustomerDeletesOthersRating_ThrowsRatingNotFoundException() {
            // Rating belongs to user 2L, caller is user 99L with CUSTOMER role
            when(ratingRepository.findById(1L)).thenReturn(Optional.of(rating));

            assertThatThrownBy(() -> ratingService.delete(99L, UserRole.CUSTOMER, 1L))
                    .isInstanceOf(RatingNotFoundException.class)
                    .hasMessageContaining("1");

            verify(ratingRepository, never()).delete(any(Rating.class));
        }
    }

    // ---------------------------------------------------------------------------
    // listBySession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("listBySession")
    class ListBySession {

        @Test
        @DisplayName("session exists — returns PageResponse mapped from repository page")
        void listBySession_SessionExists_ReturnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Rating> page = new PageImpl<>(List.of(rating), pageable, 1);

            when(classSessionRepository.existsById(10L)).thenReturn(true);
            when(ratingRepository.findBySessionId(10L, pageable)).thenReturn(page);

            PageResponse<RatingResponse> result = ratingService.listBySession(10L, pageable);

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).sessionId()).isEqualTo(10L);
            assertThat(result.content().get(0).score()).isEqualTo(4);
        }

        @Test
        @DisplayName("session not found — throws SessionNotFoundException")
        void listBySession_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> ratingService.listBySession(999L, PageRequest.of(0, 10)))
                    .isInstanceOf(SessionNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ---------------------------------------------------------------------------
    // myRatings
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("myRatings")
    class MyRatings {

        @Test
        @DisplayName("returns PageResponse mapped from repository page for callerId")
        void myRatings_CallerIdProvided_ReturnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Rating> page = new PageImpl<>(List.of(rating), pageable, 1);

            when(ratingRepository.findByUserId(2L, pageable)).thenReturn(page);

            PageResponse<RatingResponse> result = ratingService.myRatings(2L, pageable);

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).userId()).isEqualTo(2L);
            assertThat(result.content().get(0).score()).isEqualTo(4);
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
