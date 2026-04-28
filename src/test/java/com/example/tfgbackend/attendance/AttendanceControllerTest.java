package com.example.tfgbackend.attendance;

import com.example.tfgbackend.attendance.dto.AttendanceEntryRequest;
import com.example.tfgbackend.attendance.dto.AttendanceResponse;
import com.example.tfgbackend.attendance.dto.RecordAttendanceRequest;
import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.exception.AttendanceNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotAttendableException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.AttendanceStatus;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AttendanceController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 *
 * <p>Written in the TDD red phase — the real service throws
 * {@link UnsupportedOperationException} until the green phase is complete.
 */
@WebMvcTest(AttendanceController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AttendanceControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean AttendanceService attendanceService;
    @MockitoBean JwtService jwtService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE = "/api/v1/class-sessions/100/attendance";

    // ---------------------------------------------------------------------------
    // Authentication helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "alice@test.com", UserRole.CUSTOMER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "admin@test.com", UserRole.ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken instructorAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "instructor@test.com", UserRole.INSTRUCTOR);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")));
    }

    // ---------------------------------------------------------------------------
    // Fixture helper
    // ---------------------------------------------------------------------------

    private AttendanceResponse attendanceResponse(Long id, Long userId, AttendanceStatus status) {
        return new AttendanceResponse(id, userId, 100L, status, Instant.now());
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/class-sessions/{sessionId}/attendance
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/class-sessions/{sessionId}/attendance — recordAttendance")
    class RecordAttendance {

        private RecordAttendanceRequest validRequest() {
            return new RecordAttendanceRequest(
                    List.of(new AttendanceEntryRequest(1L, AttendanceStatus.ATTENDED)));
        }

        @Test
        @DisplayName("instructor submits attendance — returns 201 with body")
        void recordAttendance_InstructorHappyPath_Returns201() throws Exception {
            AttendanceResponse resp = attendanceResponse(200L, 1L, AttendanceStatus.ATTENDED);
            when(attendanceService.recordAttendance(eq(100L), any())).thenReturn(List.of(resp));

            mvc.perform(post(BASE)
                            .with(authentication(instructorAuth(10L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].id").value(200))
                    .andExpect(jsonPath("$[0].userId").value(1))
                    .andExpect(jsonPath("$[0].status").value("ATTENDED"));
        }

        @Test
        @DisplayName("admin submits attendance — returns 201 with body")
        void recordAttendance_AdminHappyPath_Returns201() throws Exception {
            AttendanceResponse resp = attendanceResponse(201L, 1L, AttendanceStatus.ATTENDED);
            when(attendanceService.recordAttendance(eq(100L), any())).thenReturn(List.of(resp));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].id").value(201));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void recordAttendance_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("customer role is forbidden — returns 403")
        void recordAttendance_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("empty attendances list fails validation — returns 400")
        void recordAttendance_EmptyAttendancesList_Returns400() throws Exception {
            RecordAttendanceRequest emptyRequest = new RecordAttendanceRequest(List.of());

            mvc.perform(post(BASE)
                            .with(authentication(instructorAuth(10L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(emptyRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("session not found — returns 404")
        void recordAttendance_SessionNotFound_Returns404() throws Exception {
            when(attendanceService.recordAttendance(eq(100L), any()))
                    .thenThrow(new SessionNotFoundException(100L));

            mvc.perform(post(BASE)
                            .with(authentication(instructorAuth(10L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("session in wrong status (SCHEDULED/CANCELLED) — returns 409")
        void recordAttendance_WrongSessionStatus_Returns409() throws Exception {
            when(attendanceService.recordAttendance(eq(100L), any()))
                    .thenThrow(new SessionNotAttendableException(100L, SessionStatus.SCHEDULED));

            mvc.perform(post(BASE)
                            .with(authentication(instructorAuth(10L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SessionNotAttendable"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-sessions/{sessionId}/attendance
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-sessions/{sessionId}/attendance — getSessionAttendance")
    class GetSessionAttendance {

        @Test
        @DisplayName("instructor retrieves attendance — returns 200 with list")
        void getSessionAttendance_Instructor_Returns200WithList() throws Exception {
            AttendanceResponse r1 = attendanceResponse(200L, 1L, AttendanceStatus.ATTENDED);
            AttendanceResponse r2 = attendanceResponse(201L, 2L, AttendanceStatus.NO_SHOW);
            when(attendanceService.getSessionAttendance(100L)).thenReturn(List.of(r1, r2));

            mvc.perform(get(BASE)
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(200))
                    .andExpect(jsonPath("$[0].status").value("ATTENDED"))
                    .andExpect(jsonPath("$[1].id").value(201))
                    .andExpect(jsonPath("$[1].status").value("NO_SHOW"));
        }

        @Test
        @DisplayName("admin retrieves attendance — returns 200")
        void getSessionAttendance_Admin_Returns200() throws Exception {
            when(attendanceService.getSessionAttendance(100L)).thenReturn(List.of());

            mvc.perform(get(BASE)
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getSessionAttendance_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("customer role is forbidden — returns 403")
        void getSessionAttendance_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(get(BASE)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("session not found — returns 404")
        void getSessionAttendance_SessionNotFound_Returns404() throws Exception {
            when(attendanceService.getSessionAttendance(100L))
                    .thenThrow(new SessionNotFoundException(100L));

            mvc.perform(get(BASE)
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/class-sessions/{sessionId}/attendance/{attendanceId}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/class-sessions/{sessionId}/attendance/{attendanceId} — deleteAttendance")
    class DeleteAttendance {

        @Test
        @DisplayName("admin deletes attendance record — returns 204")
        void deleteAttendance_Admin_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/200")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("instructor is forbidden from deleting — returns 403")
        void deleteAttendance_InstructorForbidden_Returns403() throws Exception {
            mvc.perform(delete(BASE + "/200")
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("customer is forbidden from deleting — returns 403")
        void deleteAttendance_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(delete(BASE + "/200")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void deleteAttendance_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(BASE + "/200"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("attendance record not found — returns 404")
        void deleteAttendance_NotFound_Returns404() throws Exception {
            doThrow(new AttendanceNotFoundException(999L))
                    .when(attendanceService).deleteAttendance(eq(100L), eq(999L));

            mvc.perform(delete(BASE + "/999")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("AttendanceNotFound"));
        }

        @Test
        @DisplayName("attendance belongs to different session — returns 404")
        void deleteAttendance_RecordBelongsToDifferentSession_Returns404() throws Exception {
            doThrow(new AttendanceNotFoundException(200L))
                    .when(attendanceService).deleteAttendance(eq(100L), eq(200L));

            mvc.perform(delete(BASE + "/200")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("AttendanceNotFound"));
        }
    }
}
