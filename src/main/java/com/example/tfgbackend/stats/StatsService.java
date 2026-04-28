package com.example.tfgbackend.stats;

import com.example.tfgbackend.attendance.Attendance;
import com.example.tfgbackend.attendance.AttendanceRepository;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.enums.AttendanceStatus;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.stats.dto.AttendanceHistoryEntry;
import com.example.tfgbackend.stats.dto.ClassSessionInfo;
import com.example.tfgbackend.stats.dto.ClassTypeInfo;
import com.example.tfgbackend.stats.dto.UserStatsResponse;
import com.example.tfgbackend.subscription.Subscription;
import com.example.tfgbackend.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsService {

    private final AttendanceRepository attendanceRepository;
    private final BookingRepository bookingRepository;
    private final SubscriptionRepository subscriptionRepository;

    public UserStatsResponse getUserStats(Long userId) {
        long totalBookings = bookingRepository.countByUserId(userId);
        long totalAttended = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.ATTENDED);
        long totalNoShows = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.NO_SHOW);
        long totalCancellations = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.CANCELLED);

        // attendanceRate: attended / (attended + noShows); 0.0 when denominator is 0
        long denominator = totalAttended + totalNoShows;
        double attendanceRate = denominator == 0 ? 0.0 : (double) totalAttended / denominator;

        int currentStreak = computeStreak(userId);

        String favoriteClassType = attendanceRepository.findFavoriteClassTypeByUserId(userId).orElse(null);

        // Count bookings created in the current calendar month (UTC boundaries)
        YearMonth currentMonth = YearMonth.now();
        Instant monthStart = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        long classesBookedThisMonth = bookingRepository.countByUserIdAndBookedAtBetween(userId, monthStart, monthEnd);

        Integer classesRemainingThisMonth = computeClassesRemaining(userId);

        return new UserStatsResponse(
                totalBookings,
                totalAttended,
                totalNoShows,
                totalCancellations,
                attendanceRate,
                currentStreak,
                favoriteClassType,
                classesBookedThisMonth,
                classesRemainingThisMonth
        );
    }

    public PageResponse<AttendanceHistoryEntry> getAttendanceHistory(Long userId, Pageable pageable) {
        Page<Attendance> page = attendanceRepository.findByUserIdOrderByRecordedAtDesc(userId, pageable);
        Page<AttendanceHistoryEntry> mapped = page.map(this::toHistoryEntry);
        return PageResponse.of(mapped);
    }

    // Walk backwards from today, counting consecutive days with at least one ATTENDED record.
    // The streak counts today if attended, then yesterday, and stops at the first missing day.
    private int computeStreak(Long userId) {
        List<Attendance> attended = attendanceRepository.findAllByUserIdAndStatus(userId, AttendanceStatus.ATTENDED);
        if (attended.isEmpty()) {
            return 0;
        }

        ZoneId zone = ZoneId.systemDefault();
        Set<LocalDate> attendedDays = attended.stream()
                .map(a -> a.getRecordedAt().atZone(zone).toLocalDate())
                .collect(Collectors.toSet());

        LocalDate cursor = LocalDate.now(zone);
        int streak = 0;
        while (attendedDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    // Returns null when there is no active subscription or the plan is unlimited.
    // Clamped to 0 when classesUsed exceeds classesPerMonth.
    private Integer computeClassesRemaining(Long userId) {
        Optional<Subscription> subOpt = subscriptionRepository
                .findTopByUserIdAndStatusOrderByStartDateDesc(userId, SubscriptionStatus.ACTIVE);
        if (subOpt.isEmpty()) {
            return null;
        }
        Subscription sub = subOpt.get();
        MembershipPlan plan = sub.getPlan();
        if (plan.getClassesPerMonth() == null) {
            // Unlimited plan
            return null;
        }
        return Math.max(0, plan.getClassesPerMonth() - sub.getClassesUsedThisMonth());
    }

    private AttendanceHistoryEntry toHistoryEntry(Attendance attendance) {
        var session = attendance.getSession();
        var classType = session.getClassType();
        ClassTypeInfo classTypeInfo = new ClassTypeInfo(
                classType.getId(),
                classType.getName(),
                classType.getLevel()
        );
        ClassSessionInfo sessionInfo = new ClassSessionInfo(
                session.getId(),
                session.getStartTime(),
                session.getDurationMinutes(),
                session.getRoom(),
                classTypeInfo
        );
        return new AttendanceHistoryEntry(
                attendance.getId(),
                attendance.getRecordedAt(),
                attendance.getStatus(),
                sessionInfo
        );
    }
}
