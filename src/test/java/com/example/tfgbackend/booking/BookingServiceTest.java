package com.example.tfgbackend.booking;

import com.example.tfgbackend.booking.dto.BookingResponse;
import com.example.tfgbackend.booking.dto.ClassSessionSummary;
import com.example.tfgbackend.booking.dto.RosterEntryResponse;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.classtype.ClassType;
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
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.subscription.Subscription;
import com.example.tfgbackend.subscription.SubscriptionRepository;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import com.example.tfgbackend.waitlist.WaitlistEntry;
import com.example.tfgbackend.waitlist.WaitlistRepository;
import com.example.tfgbackend.waitlist.dto.WaitlistEntryResponse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link BookingService}. No Spring context — all collaborators are mocked.
 *
 * <p>The service does not yet exist; this test suite is intentionally written ahead of the
 * implementation (TDD). Compilation will fail until the production classes are created.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock UserRepository userRepository;
    @Mock WaitlistRepository waitlistRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock com.example.tfgbackend.notification.NotificationService notificationService;

    @InjectMocks BookingService bookingService;

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    private User alice;
    private User admin;
    private ClassType spinning;
    private Gym gymFixture;
    private ClassSession scheduledSession;
    private ClassSession cancelledSession;
    private ClassSession fullSession;

    @BeforeEach
    void setUp() {
        alice = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER);
        admin = buildUser(99L, "Admin", "admin@test.com", UserRole.ADMIN);

        spinning = ClassType.builder()
                .name("Spinning").description("Cycling class").level("INTERMEDIATE").build();
        setId(spinning, 10L);

        gymFixture = Gym.builder()
                .name("Downtown Gym").address("123 Main St").city("Madrid").build();
        setId(gymFixture, 5L);

        scheduledSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45).maxCapacity(10).room("1A")
                .status(SessionStatus.SCHEDULED)
                .classType(spinning).gym(gymFixture).build();
        setId(scheduledSession, 100L);

        cancelledSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(2))
                .durationMinutes(45).maxCapacity(10).room("2B")
                .status(SessionStatus.CANCELLED)
                .classType(spinning).gym(gymFixture).build();
        setId(cancelledSession, 101L);

        fullSession = ClassSession.builder()
                .startTime(LocalDateTime.now().plusDays(3))
                .durationMinutes(45).maxCapacity(2).room("3C")
                .status(SessionStatus.SCHEDULED)
                .classType(spinning).gym(gymFixture).build();
        setId(fullSession, 102L);

        // Default: alice has an unlimited active subscription (classesPerMonth=null)
        MembershipPlan unlimitedPlan = MembershipPlan.builder()
                .name("Unlimited").priceMonthly(BigDecimal.valueOf(50))
                .classesPerMonth(null).durationMonths(1).build();
        Subscription activeSub = Subscription.builder()
                .user(alice).plan(unlimitedPlan).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now()).renewalDate(LocalDate.now().plusMonths(1))
                .classesUsedThisMonth(0).build();
        lenient().when(subscriptionRepository.findByUserIdAndStatus(eq(1L), eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(Optional.of(activeSub));
    }

    // ---------------------------------------------------------------------------
    // createBooking
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createBooking")
    class CreateBooking {

        @Test
        @DisplayName("happy path: creates CONFIRMED booking and returns response")
        void createBooking_ValidSessionAndUser_ReturnsConfirmedBookingResponse() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(5L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(bookingRepository.save(any())).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                setId(b, 200L);
                return b;
            });

            BookingResponse response = bookingService.createBooking(1L, 100L);

            assertThat(response.id()).isEqualTo(200L);
            assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(response.classSession().id()).isEqualTo(100L);
            assertThat(response.classSession().classTypeName()).isEqualTo("Spinning");
            assertThat(response.classSession().gymName()).isEqualTo("Downtown Gym");

            ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(captor.getValue().getUser()).isEqualTo(alice);
            assertThat(captor.getValue().getSession()).isEqualTo(scheduledSession);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void createBooking_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.createBooking(1L, 999L))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("session not SCHEDULED throws SessionNotBookableException")
        void createBooking_SessionNotScheduled_ThrowsSessionNotBookableException() {
            when(classSessionRepository.findById(101L)).thenReturn(Optional.of(cancelledSession));

            assertThatThrownBy(() -> bookingService.createBooking(1L, 101L))
                    .isInstanceOf(SessionNotBookableException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("active booking already exists throws AlreadyBookedException")
        void createBooking_ActiveBookingExists_ThrowsAlreadyBookedException() {
            Booking existing = buildBooking(50L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> bookingService.createBooking(1L, 100L))
                    .isInstanceOf(AlreadyBookedException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelled booking for same session does not block re-booking")
        void createBooking_OnlyCancelledBookingExists_AllowsNewBooking() {
            Booking cancelled = buildBooking(50L, alice, scheduledSession, BookingStatus.CANCELLED);
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(cancelled));
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(3L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(bookingRepository.save(any())).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                setId(b, 201L);
                return b;
            });

            BookingResponse response = bookingService.createBooking(1L, 100L);

            assertThat(response).isNotNull();
            verify(bookingRepository).save(any());
        }

        @Test
        @DisplayName("session at capacity throws ClassFullException")
        void createBooking_SessionAtCapacity_ThrowsClassFullException() {
            when(classSessionRepository.findById(102L)).thenReturn(Optional.of(fullSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 102L)).thenReturn(Optional.empty());
            // maxCapacity is 2, countConfirmed returns 2
            when(bookingRepository.countConfirmedBySessionId(102L)).thenReturn(2L);

            assertThatThrownBy(() -> bookingService.createBooking(1L, 102L))
                    .isInstanceOf(ClassFullException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("user not found throws RuntimeException")
        void createBooking_UserNotFound_ThrowsRuntimeException() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.createBooking(1L, 100L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found: 1");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("session with null gym returns null gymName in response")
        void createBooking_SessionWithNullGym_ReturnsNullGymName() {
            ClassSession noGymSession = ClassSession.builder()
                    .startTime(LocalDateTime.now().plusDays(1))
                    .durationMinutes(45).maxCapacity(10).room("1A")
                    .status(SessionStatus.SCHEDULED)
                    .classType(spinning).gym(null).build();
            setId(noGymSession, 200L);

            when(classSessionRepository.findById(200L)).thenReturn(Optional.of(noGymSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 200L)).thenReturn(Optional.empty());
            when(bookingRepository.countConfirmedBySessionId(200L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(bookingRepository.save(any())).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                setId(b, 300L);
                return b;
            });

            BookingResponse response = bookingService.createBooking(1L, 200L);

            assertThat(response.classSession().gymName()).isNull();
        }

        @Test
        @DisplayName("capped plan with classes remaining: booking succeeds and counter increments")
        void createBooking_CappedPlanWithRemainingClasses_SucceedsAndIncrementsCounter() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(bookingRepository.save(any())).thenAnswer(inv -> {
                Booking b = inv.getArgument(0);
                setId(b, 200L);
                return b;
            });

            MembershipPlan cappedPlan = MembershipPlan.builder()
                    .name("Basic").priceMonthly(BigDecimal.valueOf(20))
                    .classesPerMonth(10).durationMonths(1).build();
            Subscription cappedSub = Subscription.builder()
                    .user(alice).plan(cappedPlan).status(SubscriptionStatus.ACTIVE)
                    .startDate(LocalDate.now()).renewalDate(LocalDate.now().plusMonths(1))
                    .classesUsedThisMonth(5).build();
            when(subscriptionRepository.findByUserIdAndStatus(eq(1L), eq(SubscriptionStatus.ACTIVE)))
                    .thenReturn(Optional.of(cappedSub));

            bookingService.createBooking(1L, 100L);

            assertThat(cappedSub.getClassesUsedThisMonth()).isEqualTo(6);
        }

        @Test
        @DisplayName("no active subscription throws NoActiveSubscriptionException")
        void createBooking_NoActiveSubscription_ThrowsNoActiveSubscriptionException() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(subscriptionRepository.findByUserIdAndStatus(eq(1L), eq(SubscriptionStatus.ACTIVE)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.createBooking(1L, 100L))
                    .isInstanceOf(NoActiveSubscriptionException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("monthly class limit reached throws MonthlyClassLimitReachedException")
        void createBooking_MonthlyLimitReached_ThrowsMonthlyClassLimitReachedException() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(bookingRepository.countConfirmedBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

            MembershipPlan cappedPlan = MembershipPlan.builder()
                    .name("Basic").priceMonthly(BigDecimal.valueOf(20))
                    .classesPerMonth(10).durationMonths(1).build();
            Subscription fullSub = Subscription.builder()
                    .user(alice).plan(cappedPlan).status(SubscriptionStatus.ACTIVE)
                    .startDate(LocalDate.now()).renewalDate(LocalDate.now().plusMonths(1))
                    .classesUsedThisMonth(10).build();
            when(subscriptionRepository.findByUserIdAndStatus(eq(1L), eq(SubscriptionStatus.ACTIVE)))
                    .thenReturn(Optional.of(fullSub));

            assertThatThrownBy(() -> bookingService.createBooking(1L, 100L))
                    .isInstanceOf(MonthlyClassLimitReachedException.class);

            verify(bookingRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // cancelBooking
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("cancelBooking")
    class CancelBooking {

        @Test
        @DisplayName("happy path: owner cancels own booking, no waitlist")
        void cancelBooking_OwnerCancels_NoWaitlist_StatusSetToCancelled() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(100L)).thenReturn(List.of());

            bookingService.cancelBooking(200L, 1L, false);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(bookingRepository).save(booking);
            verify(waitlistRepository, never()).delete(any());
        }

        @Test
        @DisplayName("happy path: cancel promotes first waitlist entry to CONFIRMED booking")
        void cancelBooking_WithWaitlist_PromotesFirstEntry() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);

            User bob = buildUser(2L, "Bob", "bob@test.com", UserRole.CUSTOMER);
            WaitlistEntry first = buildWaitlistEntry(10L, bob, scheduledSession, 1);
            WaitlistEntry second = buildWaitlistEntry(11L, buildUser(3L, "Carol", "carol@test.com", UserRole.CUSTOMER), scheduledSession, 2);

            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(100L))
                    .thenReturn(List.of(first, second));
            when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            bookingService.cancelBooking(200L, 1L, false);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

            ArgumentCaptor<Booking> promotedCaptor = ArgumentCaptor.forClass(Booking.class);
            // Two saves: one for cancellation, one for the new CONFIRMED booking
            verify(bookingRepository, org.mockito.Mockito.times(2)).save(promotedCaptor.capture());

            List<Booking> saved = promotedCaptor.getAllValues();
            Booking promoted = saved.stream()
                    .filter(b -> b.getUser().equals(bob))
                    .findFirst()
                    .orElseThrow();
            assertThat(promoted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(promoted.getSession()).isEqualTo(scheduledSession);

            verify(waitlistRepository).delete(first);
        }

        @Test
        @DisplayName("admin can cancel another user's booking")
        void cancelBooking_AdminCancelsOtherUserBooking_Succeeds() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(100L)).thenReturn(List.of());

            // Admin has userId=99, booking belongs to alice (userId=1)
            bookingService.cancelBooking(200L, 99L, true);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(bookingRepository).save(booking);
        }

        @Test
        @DisplayName("customer cannot cancel another user's booking — throws BookingNotFoundException")
        void cancelBooking_CustomerTriesOtherUserBooking_ThrowsBookingNotFoundException() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));

            // requestingUserId=2 is not alice (1) and isAdmin=false
            assertThatThrownBy(() -> bookingService.cancelBooking(200L, 2L, false))
                    .isInstanceOf(BookingNotFoundException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("booking not found throws BookingNotFoundException")
        void cancelBooking_BookingNotFound_ThrowsBookingNotFoundException() {
            when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.cancelBooking(999L, 1L, false))
                    .isInstanceOf(BookingNotFoundException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("already cancelled booking throws BookingAlreadyCancelledException")
        void cancelBooking_AlreadyCancelled_ThrowsBookingAlreadyCancelledException() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CANCELLED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.cancelBooking(200L, 1L, false))
                    .isInstanceOf(BookingAlreadyCancelledException.class);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("session already started: cancels but does not decrement class credit")
        void cancelBooking_SessionAlreadyStarted_DoesNotDecrementClassCredit() {
            ClassSession pastSession = ClassSession.builder()
                    .startTime(LocalDateTime.now().minusHours(1))
                    .durationMinutes(45).maxCapacity(10).room("1A")
                    .status(SessionStatus.SCHEDULED)
                    .classType(spinning).gym(gymFixture).build();
            setId(pastSession, 103L);

            Booking booking = buildBooking(200L, alice, pastSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));
            when(waitlistRepository.findBySessionIdOrderByPositionAsc(103L)).thenReturn(List.of());

            bookingService.cancelBooking(200L, 1L, false);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(subscriptionRepository, never()).findByUserIdAndStatus(any(), any());
        }
    }

    // ---------------------------------------------------------------------------
    // getMyBookings
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getMyBookings")
    class GetMyBookings {

        @Test
        @DisplayName("no status filter returns all bookings for the user, paginated")
        void getMyBookings_NoStatusFilter_ReturnsAllUserBookings() {
            Booking b1 = buildBooking(1L, alice, scheduledSession, BookingStatus.CONFIRMED);
            Booking b2 = buildBooking(2L, alice, scheduledSession, BookingStatus.CANCELLED);
            Page<Booking> page = new PageImpl<>(List.of(b1, b2), PageRequest.of(0, 10), 2);

            when(bookingRepository.findByUserIdOrderByBookedAtDesc(eq(1L), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<BookingResponse> response = bookingService.getMyBookings(1L, null, PageRequest.of(0, 10));

            assertThat(response.content()).hasSize(2);
            assertThat(response.totalElements()).isEqualTo(2);
            verify(bookingRepository).findByUserIdOrderByBookedAtDesc(eq(1L), any(Pageable.class));
            verify(bookingRepository, never()).findByUserIdAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("status filter delegates to findByUserIdAndStatus")
        void getMyBookings_WithStatusFilter_DelegatesToFilteredQuery() {
            Booking b1 = buildBooking(1L, alice, scheduledSession, BookingStatus.CONFIRMED);
            Page<Booking> page = new PageImpl<>(List.of(b1), PageRequest.of(0, 10), 1);

            when(bookingRepository.findByUserIdAndStatus(eq(1L), eq(BookingStatus.CONFIRMED), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<BookingResponse> response = bookingService.getMyBookings(
                    1L, BookingStatus.CONFIRMED, PageRequest.of(0, 10));

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).status()).isEqualTo(BookingStatus.CONFIRMED);
            verify(bookingRepository).findByUserIdAndStatus(
                    eq(1L), eq(BookingStatus.CONFIRMED), any(Pageable.class));
            verify(bookingRepository, never()).findByUserIdOrderByBookedAtDesc(any(), any());
        }

        @Test
        @DisplayName("empty result returns page with no content")
        void getMyBookings_NoneFound_ReturnsEmptyPage() {
            Page<Booking> empty = Page.empty(PageRequest.of(0, 10));
            when(bookingRepository.findByUserIdOrderByBookedAtDesc(eq(1L), any(Pageable.class)))
                    .thenReturn(empty);

            PageResponse<BookingResponse> response = bookingService.getMyBookings(1L, null, PageRequest.of(0, 10));

            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // getBookingById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getBookingById")
    class GetBookingById {

        @Test
        @DisplayName("owner retrieves own booking successfully")
        void getBookingById_OwnerRequests_ReturnsBookingResponse() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));

            BookingResponse response = bookingService.getBookingById(200L, 1L, false);

            assertThat(response.id()).isEqualTo(200L);
            assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("admin retrieves any booking successfully")
        void getBookingById_AdminRequests_ReturnsBookingResponse() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));

            BookingResponse response = bookingService.getBookingById(200L, 99L, true);

            assertThat(response.id()).isEqualTo(200L);
        }

        @Test
        @DisplayName("customer requesting another user's booking throws BookingNotFoundException")
        void getBookingById_CustomerRequestsOtherUserBooking_ThrowsBookingNotFoundException() {
            Booking booking = buildBooking(200L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));

            // requestingUserId=2 is not alice (1), not admin
            assertThatThrownBy(() -> bookingService.getBookingById(200L, 2L, false))
                    .isInstanceOf(BookingNotFoundException.class);
        }

        @Test
        @DisplayName("booking not found throws BookingNotFoundException")
        void getBookingById_NotFound_ThrowsBookingNotFoundException() {
            when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.getBookingById(999L, 1L, false))
                    .isInstanceOf(BookingNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // getSessionRoster
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getSessionRoster")
    class GetSessionRoster {

        @Test
        @DisplayName("happy path: returns paginated roster entries for a session")
        void getSessionRoster_ValidSession_ReturnsPaginatedRoster() {
            Booking b1 = buildBooking(1L, alice, scheduledSession, BookingStatus.CONFIRMED);
            Page<Booking> page = new PageImpl<>(List.of(b1), PageRequest.of(0, 10), 1);

            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findBySessionId(eq(100L), any(Pageable.class))).thenReturn(page);

            PageResponse<RosterEntryResponse> response =
                    bookingService.getSessionRoster(100L, PageRequest.of(0, 10));

            assertThat(response.content()).hasSize(1);
            RosterEntryResponse entry = response.content().get(0);
            assertThat(entry.bookingId()).isEqualTo(1L);
            assertThat(entry.userId()).isEqualTo(1L);
            assertThat(entry.userFullName()).isEqualTo("Alice");
            assertThat(entry.userEmail()).isEqualTo("alice@test.com");
            assertThat(entry.status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void getSessionRoster_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.getSessionRoster(999L, PageRequest.of(0, 10)))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(bookingRepository, never()).findBySessionId(any(), any());
        }
    }

    // ---------------------------------------------------------------------------
    // joinWaitlist
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("joinWaitlist")
    class JoinWaitlist {

        @Test
        @DisplayName("happy path: first entry gets position 1")
        void joinWaitlist_EmptyWaitlist_CreatesEntryWithPosition1() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.countBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(waitlistRepository.save(any())).thenAnswer(inv -> {
                WaitlistEntry e = inv.getArgument(0);
                setId(e, 50L);
                return e;
            });

            WaitlistEntryResponse response = bookingService.joinWaitlist(1L, 100L);

            assertThat(response.id()).isEqualTo(50L);
            assertThat(response.position()).isEqualTo(1);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.classSession().id()).isEqualTo(100L);

            ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);
            verify(waitlistRepository).save(captor.capture());
            assertThat(captor.getValue().getPosition()).isEqualTo(1);
        }

        @Test
        @DisplayName("second entry gets position = existing count + 1")
        void joinWaitlist_ExistingEntries_AssignsNextPosition() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.countBySessionId(100L)).thenReturn(3L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(waitlistRepository.save(any())).thenAnswer(inv -> {
                WaitlistEntry e = inv.getArgument(0);
                setId(e, 51L);
                return e;
            });

            WaitlistEntryResponse response = bookingService.joinWaitlist(1L, 100L);

            assertThat(response.position()).isEqualTo(4);
        }

        @Test
        @DisplayName("session not found throws SessionNotFoundException")
        void joinWaitlist_SessionNotFound_ThrowsSessionNotFoundException() {
            when(classSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.joinWaitlist(1L, 999L))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("session not SCHEDULED throws SessionNotBookableException")
        void joinWaitlist_SessionNotScheduled_ThrowsSessionNotBookableException() {
            when(classSessionRepository.findById(101L)).thenReturn(Optional.of(cancelledSession));

            assertThatThrownBy(() -> bookingService.joinWaitlist(1L, 101L))
                    .isInstanceOf(SessionNotBookableException.class);

            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("active booking already exists throws AlreadyBookedException")
        void joinWaitlist_ActiveBookingExists_ThrowsAlreadyBookedException() {
            Booking existing = buildBooking(50L, alice, scheduledSession, BookingStatus.CONFIRMED);
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> bookingService.joinWaitlist(1L, 100L))
                    .isInstanceOf(AlreadyBookedException.class);

            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("already on waitlist throws AlreadyOnWaitlistException")
        void joinWaitlist_AlreadyOnWaitlist_ThrowsAlreadyOnWaitlistException() {
            WaitlistEntry existing = buildWaitlistEntry(10L, alice, scheduledSession, 1);
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> bookingService.joinWaitlist(1L, 100L))
                    .isInstanceOf(AlreadyOnWaitlistException.class);

            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelled booking exists allows joining waitlist")
        void joinWaitlist_OnlyCancelledBookingExists_AllowsJoiningWaitlist() {
            Booking cancelled = buildBooking(50L, alice, scheduledSession, BookingStatus.CANCELLED);
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(cancelled));
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.countBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(waitlistRepository.save(any())).thenAnswer(inv -> {
                WaitlistEntry e = inv.getArgument(0);
                setId(e, 52L);
                return e;
            });

            WaitlistEntryResponse response = bookingService.joinWaitlist(1L, 100L);

            assertThat(response).isNotNull();
            verify(waitlistRepository).save(any());
        }

        @Test
        @DisplayName("user not found throws RuntimeException")
        void joinWaitlist_UserNotFound_ThrowsRuntimeException() {
            when(classSessionRepository.findById(100L)).thenReturn(Optional.of(scheduledSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());
            when(waitlistRepository.countBySessionId(100L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.joinWaitlist(1L, 100L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found: 1");

            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("session with null gym returns null gymName in waitlist response")
        void joinWaitlist_SessionWithNullGym_ReturnsNullGymName() {
            ClassSession noGymSession = ClassSession.builder()
                    .startTime(LocalDateTime.now().plusDays(1))
                    .durationMinutes(45).maxCapacity(10).room("1A")
                    .status(SessionStatus.SCHEDULED)
                    .classType(spinning).gym(null).build();
            setId(noGymSession, 200L);

            when(classSessionRepository.findById(200L)).thenReturn(Optional.of(noGymSession));
            when(bookingRepository.findByUserIdAndSessionId(1L, 200L)).thenReturn(Optional.empty());
            when(waitlistRepository.findByUserIdAndSessionId(1L, 200L)).thenReturn(Optional.empty());
            when(waitlistRepository.countBySessionId(200L)).thenReturn(0L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(waitlistRepository.save(any())).thenAnswer(inv -> {
                WaitlistEntry e = inv.getArgument(0);
                setId(e, 55L);
                return e;
            });

            WaitlistEntryResponse response = bookingService.joinWaitlist(1L, 200L);

            assertThat(response.classSession().gymName()).isNull();
        }
    }

    // ---------------------------------------------------------------------------
    // leaveWaitlist
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("leaveWaitlist")
    class LeaveWaitlist {

        @Test
        @DisplayName("happy path: entry is deleted")
        void leaveWaitlist_EntryExists_DeletesEntry() {
            WaitlistEntry entry = buildWaitlistEntry(10L, alice, scheduledSession, 1);
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.of(entry));

            bookingService.leaveWaitlist(1L, 100L);

            verify(waitlistRepository).delete(entry);
        }

        @Test
        @DisplayName("entry not found throws WaitlistEntryNotFoundException")
        void leaveWaitlist_EntryNotFound_ThrowsWaitlistEntryNotFoundException() {
            when(waitlistRepository.findByUserIdAndSessionId(1L, 100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.leaveWaitlist(1L, 100L))
                    .isInstanceOf(WaitlistEntryNotFoundException.class);

            verify(waitlistRepository, never()).delete(any());
        }
    }

    // ---------------------------------------------------------------------------
    // getMyWaitlistEntries
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getMyWaitlistEntries")
    class GetMyWaitlistEntries {

        @Test
        @DisplayName("returns all waitlist entries for the user")
        void getMyWaitlistEntries_UserHasEntries_ReturnsAllEntries() {
            WaitlistEntry e1 = buildWaitlistEntry(10L, alice, scheduledSession, 1);
            WaitlistEntry e2 = buildWaitlistEntry(11L, alice, fullSession, 2);
            when(waitlistRepository.findByUserId(1L)).thenReturn(List.of(e1, e2));

            List<WaitlistEntryResponse> responses = bookingService.getMyWaitlistEntries(1L);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).id()).isEqualTo(10L);
            assertThat(responses.get(0).position()).isEqualTo(1);
            assertThat(responses.get(1).id()).isEqualTo(11L);
            assertThat(responses.get(1).position()).isEqualTo(2);
        }

        @Test
        @DisplayName("user with no entries returns empty list")
        void getMyWaitlistEntries_NoneFound_ReturnsEmptyList() {
            when(waitlistRepository.findByUserId(1L)).thenReturn(List.of());

            List<WaitlistEntryResponse> responses = bookingService.getMyWaitlistEntries(1L);

            assertThat(responses).isEmpty();
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
