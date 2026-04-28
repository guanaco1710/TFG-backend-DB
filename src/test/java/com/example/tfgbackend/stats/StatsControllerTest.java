package com.example.tfgbackend.stats;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.AttendanceStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.stats.dto.AttendanceHistoryEntry;
import com.example.tfgbackend.stats.dto.ClassSessionInfo;
import com.example.tfgbackend.stats.dto.ClassTypeInfo;
import com.example.tfgbackend.stats.dto.UserStatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class StatsControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean StatsService statsService;
    @MockitoBean JwtService jwtService;

    private static final String BASE = "/api/v1/stats";

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "alice@test.com", UserRole.CUSTOMER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/stats/me
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/stats/me — getUserStats")
    class GetUserStats {

        @Test
        @DisplayName("authenticated customer receives 200 with all stat fields")
        void getUserStats_Authenticated_Returns200WithAllFields() throws Exception {
            UserStatsResponse stats = new UserStatsResponse(10L, 7L, 1L, 2L, 0.875, 3, "Spinning", 4L, 6);
            when(statsService.getUserStats(1L)).thenReturn(stats);

            mvc.perform(get(BASE + "/me")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBookings").value(10))
                    .andExpect(jsonPath("$.totalAttended").value(7))
                    .andExpect(jsonPath("$.totalNoShows").value(1))
                    .andExpect(jsonPath("$.totalCancellations").value(2))
                    .andExpect(jsonPath("$.attendanceRate").value(0.875))
                    .andExpect(jsonPath("$.currentStreak").value(3))
                    .andExpect(jsonPath("$.favoriteClassType").value("Spinning"))
                    .andExpect(jsonPath("$.classesBookedThisMonth").value(4))
                    .andExpect(jsonPath("$.classesRemainingThisMonth").value(6));
        }

        @Test
        @DisplayName("null classesRemainingThisMonth and null favoriteClassType are omitted from JSON")
        void getUserStats_NullOptionalFields_OmittedFromJson() throws Exception {
            UserStatsResponse stats = new UserStatsResponse(0L, 0L, 0L, 0L, 0.0, 0, null, 0L, null);
            when(statsService.getUserStats(1L)).thenReturn(stats);

            mvc.perform(get(BASE + "/me")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.classesRemainingThisMonth").doesNotExist())
                    .andExpect(jsonPath("$.favoriteClassType").doesNotExist());
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void getUserStats_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/stats/me/history
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/stats/me/history — getAttendanceHistory")
    class GetAttendanceHistory {

        @Test
        @DisplayName("authenticated customer receives 200 with paginated attendance history")
        void getAttendanceHistory_Authenticated_Returns200WithPage() throws Exception {
            ClassTypeInfo ct = new ClassTypeInfo(3L, "Spinning", "INTERMEDIATE");
            ClassSessionInfo si = new ClassSessionInfo(88L, LocalDateTime.now().minusDays(1), 45, "Sala A", ct);
            AttendanceHistoryEntry entry = new AttendanceHistoryEntry(201L, Instant.now(), AttendanceStatus.ATTENDED, si);
            PageResponse<AttendanceHistoryEntry> page = new PageResponse<>(List.of(entry), 0, 20, 1L, 1, false);

            when(statsService.getAttendanceHistory(eq(1L), any())).thenReturn(page);

            mvc.perform(get(BASE + "/me/history")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(201))
                    .andExpect(jsonPath("$.content[0].status").value("ATTENDED"))
                    .andExpect(jsonPath("$.content[0].classSession.id").value(88))
                    .andExpect(jsonPath("$.content[0].classSession.room").value("Sala A"))
                    .andExpect(jsonPath("$.content[0].classSession.durationMinutes").value(45))
                    .andExpect(jsonPath("$.content[0].classSession.classType.name").value("Spinning"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.hasMore").value(false));
        }

        @Test
        @DisplayName("empty history returns 200 with empty content array")
        void getAttendanceHistory_NoRecords_Returns200WithEmptyContent() throws Exception {
            PageResponse<AttendanceHistoryEntry> empty = new PageResponse<>(List.of(), 0, 20, 0L, 0, false);
            when(statsService.getAttendanceHistory(eq(1L), any())).thenReturn(empty);

            mvc.perform(get(BASE + "/me/history")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void getAttendanceHistory_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/me/history"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
