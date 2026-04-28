package com.example.tfgbackend.stats;

import com.example.tfgbackend.attendance.Attendance;
import com.example.tfgbackend.attendance.AttendanceRepository;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.enums.AttendanceStatus;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.stats.dto.AttendanceHistoryEntry;
import com.example.tfgbackend.stats.dto.UserStatsResponse;
import com.example.tfgbackend.subscription.Subscription;
import com.example.tfgbackend.subscription.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock AttendanceRepository attendanceRepository;
    @Mock BookingRepository bookingRepository;
    @Mock SubscriptionRepository subscriptionRepository;

    @InjectMocks StatsService statsService;

    private static final Long USER_ID = 1L;

    // ---------------------------------------------------------------------------
    // getUserStats
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getUserStats")
    class GetUserStats {

        @Test
        @DisplayName("happy path: returns all fields correctly populated")
        void getUserStats_WithFullData_ReturnsCorrectStats() {
            when(bookingRepository.countByUserId(USER_ID)).thenReturn(10L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(7L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.NO_SHOW)).thenReturn(1L);
            when(bookingRepository.countByUserIdAndStatus(USER_ID, BookingStatus.CANCELLED)).thenReturn(2L);
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED))
                    .thenReturn(List.of(attendanceOn(LocalDate.now())));
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.of("Spinning"));
            when(bookingRepository.countByUserIdAndBookedAtBetween(eq(USER_ID), any(), any())).thenReturn(3L);
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(limitedSubscription(10, 3)));

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.totalBookings()).isEqualTo(10L);
            assertThat(result.totalAttended()).isEqualTo(7L);
            assertThat(result.totalNoShows()).isEqualTo(1L);
            assertThat(result.totalCancellations()).isEqualTo(2L);
            assertThat(result.attendanceRate()).isEqualTo(7.0 / 8.0);
            assertThat(result.currentStreak()).isEqualTo(1);
            assertThat(result.favoriteClassType()).isEqualTo("Spinning");
            assertThat(result.classesBookedThisMonth()).isEqualTo(3L);
            assertThat(result.classesRemainingThisMonth()).isEqualTo(7);
        }

        @Test
        @DisplayName("no attended or no-show sessions: rate is 0.0, streak is 0, favoriteClassType is null")
        void getUserStats_NoAttendance_ZeroRateAndStreak() {
            when(bookingRepository.countByUserId(USER_ID)).thenReturn(0L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(0L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.NO_SHOW)).thenReturn(0L);
            when(bookingRepository.countByUserIdAndStatus(USER_ID, BookingStatus.CANCELLED)).thenReturn(0L);
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED))
                    .thenReturn(List.of());
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(bookingRepository.countByUserIdAndBookedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.attendanceRate()).isEqualTo(0.0);
            assertThat(result.currentStreak()).isEqualTo(0);
            assertThat(result.favoriteClassType()).isNull();
            assertThat(result.classesRemainingThisMonth()).isNull();
        }

        @Test
        @DisplayName("unlimited plan: classesRemainingThisMonth is null")
        void getUserStats_UnlimitedSubscription_ReturnsNullRemaining() {
            stubBasicCounts();
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(List.of());
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(unlimitedSubscription()));

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.classesRemainingThisMonth()).isNull();
        }

        @Test
        @DisplayName("no active subscription: classesRemainingThisMonth is null")
        void getUserStats_NoActiveSubscription_ReturnsNullRemaining() {
            stubBasicCounts();
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(List.of());
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.classesRemainingThisMonth()).isNull();
        }

        @Test
        @DisplayName("used classes exceed limit: classesRemainingThisMonth clamped to 0")
        void getUserStats_OverUsedSubscription_ReturnsZeroRemaining() {
            stubBasicCounts();
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(List.of());
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(limitedSubscription(5, 7)));

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.classesRemainingThisMonth()).isEqualTo(0);
        }

        @Test
        @DisplayName("consecutive days attended: streak equals the number of consecutive days")
        void getUserStats_ConsecutiveDaysAttended_CountsStreak() {
            LocalDate today = LocalDate.now();
            when(bookingRepository.countByUserId(USER_ID)).thenReturn(3L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(3L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.NO_SHOW)).thenReturn(0L);
            when(bookingRepository.countByUserIdAndStatus(USER_ID, BookingStatus.CANCELLED)).thenReturn(0L);
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(List.of(
                    attendanceOn(today),
                    attendanceOn(today.minusDays(1)),
                    attendanceOn(today.minusDays(2))
            ));
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(bookingRepository.countByUserIdAndBookedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.currentStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("gap in days breaks streak at first missing day")
        void getUserStats_GapInAttendedDays_BreaksStreak() {
            LocalDate today = LocalDate.now();
            when(bookingRepository.countByUserId(USER_ID)).thenReturn(2L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(2L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.NO_SHOW)).thenReturn(0L);
            when(bookingRepository.countByUserIdAndStatus(USER_ID, BookingStatus.CANCELLED)).thenReturn(0L);
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(List.of(
                    attendanceOn(today),
                    attendanceOn(today.minusDays(3)) // gap: days 1 and 2 are missing
            ));
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(bookingRepository.countByUserIdAndBookedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.currentStreak()).isEqualTo(1); // only today
        }

        @Test
        @DisplayName("no attendance today: streak is 0 even if past days have attendance")
        void getUserStats_NoAttendanceToday_StreakIsZero() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            when(bookingRepository.countByUserId(USER_ID)).thenReturn(1L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(1L);
            when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.NO_SHOW)).thenReturn(0L);
            when(bookingRepository.countByUserIdAndStatus(USER_ID, BookingStatus.CANCELLED)).thenReturn(0L);
            when(attendanceRepository.findAllByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(List.of(
                    attendanceOn(yesterday)
            ));
            when(attendanceRepository.findFavoriteClassTypeByUserId(USER_ID)).thenReturn(Optional.empty());
            when(bookingRepository.countByUserIdAndBookedAtBetween(eq(USER_ID), any(), any())).thenReturn(0L);
            when(subscriptionRepository.findTopByUserIdAndStatusOrderByStartDateDesc(USER_ID, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            UserStatsResponse result = statsService.getUserStats(USER_ID);

            assertThat(result.currentStreak()).isEqualTo(0);
        }
    }

    // ---------------------------------------------------------------------------
    // getAttendanceHistory
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getAttendanceHistory")
    class GetAttendanceHistory {

        @Test
        @DisplayName("returns a mapped PageResponse with all nested fields")
        void getAttendanceHistory_WithRecords_ReturnsMappedPage() {
            ClassType classType = ClassType.builder().name("Yoga").level("BASIC").build();
            setId(classType, 10L);

            ClassSession session = ClassSession.builder()
                    .startTime(LocalDateTime.now().minusDays(1))
                    .durationMinutes(60)
                    .room("Room B")
                    .classType(classType)
                    .status(SessionStatus.FINISHED)
                    .maxCapacity(20)
                    .build();
            setId(session, 50L);

            Attendance attendance = Attendance.builder()
                    .status(AttendanceStatus.ATTENDED)
                    .recordedAt(Instant.now().minusSeconds(3600))
                    .session(session)
                    .build();
            setId(attendance, 1L);

            Page<Attendance> page = new PageImpl<>(List.of(attendance), PageRequest.of(0, 20), 1);
            when(attendanceRepository.findByUserIdOrderByRecordedAtDesc(eq(USER_ID), any())).thenReturn(page);

            PageResponse<AttendanceHistoryEntry> result =
                    statsService.getAttendanceHistory(USER_ID, PageRequest.of(0, 20));

            assertThat(result.content()).hasSize(1);
            AttendanceHistoryEntry entry = result.content().get(0);
            assertThat(entry.id()).isEqualTo(1L);
            assertThat(entry.status()).isEqualTo(AttendanceStatus.ATTENDED);
            assertThat(entry.classSession().id()).isEqualTo(50L);
            assertThat(entry.classSession().room()).isEqualTo("Room B");
            assertThat(entry.classSession().durationMinutes()).isEqualTo(60);
            assertThat(entry.classSession().classType().name()).isEqualTo("Yoga");
            assertThat(entry.classSession().classType().level()).isEqualTo("BASIC");
            assertThat(result.totalElements()).isEqualTo(1L);
        }

        @Test
        @DisplayName("no records: returns empty page")
        void getAttendanceHistory_NoRecords_ReturnsEmptyPage() {
            Page<Attendance> emptyPage = Page.empty(PageRequest.of(0, 20));
            when(attendanceRepository.findByUserIdOrderByRecordedAtDesc(eq(USER_ID), any())).thenReturn(emptyPage);

            PageResponse<AttendanceHistoryEntry> result =
                    statsService.getAttendanceHistory(USER_ID, PageRequest.of(0, 20));

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Attendance attendanceOn(LocalDate date) {
        Instant instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(3600);
        return Attendance.builder()
                .status(AttendanceStatus.ATTENDED)
                .recordedAt(instant)
                .build();
    }

    private Subscription limitedSubscription(int classesPerMonth, int classesUsed) {
        MembershipPlan plan = MembershipPlan.builder()
                .name("Standard")
                .priceMonthly(BigDecimal.valueOf(29.99))
                .classesPerMonth(classesPerMonth)
                .build();
        return Subscription.builder()
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().withDayOfMonth(1))
                .renewalDate(LocalDate.now().plusMonths(1).withDayOfMonth(1))
                .classesUsedThisMonth(classesUsed)
                .build();
    }

    private Subscription unlimitedSubscription() {
        MembershipPlan plan = MembershipPlan.builder()
                .name("Unlimited")
                .priceMonthly(BigDecimal.valueOf(49.99))
                .classesPerMonth(null)
                .build();
        return Subscription.builder()
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().withDayOfMonth(1))
                .renewalDate(LocalDate.now().plusMonths(1).withDayOfMonth(1))
                .classesUsedThisMonth(5)
                .build();
    }

    private void stubBasicCounts() {
        when(bookingRepository.countByUserId(USER_ID)).thenReturn(5L);
        when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.ATTENDED)).thenReturn(3L);
        when(attendanceRepository.countByUserIdAndStatus(USER_ID, AttendanceStatus.NO_SHOW)).thenReturn(1L);
        when(bookingRepository.countByUserIdAndStatus(USER_ID, BookingStatus.CANCELLED)).thenReturn(1L);
        when(bookingRepository.countByUserIdAndBookedAtBetween(eq(USER_ID), any(), any())).thenReturn(2L);
    }

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
