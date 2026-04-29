package com.example.tfgbackend.classsession;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.classsession.dto.ClassSessionResponse;
import com.example.tfgbackend.classsession.dto.ClassTypeSummary;
import com.example.tfgbackend.classsession.dto.GymSummary;
import com.example.tfgbackend.classsession.dto.InstructorSummary;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.enums.UserRole;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ClassSessionController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 */
@WebMvcTest(ClassSessionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ClassSessionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ClassSessionService classSessionService;

    @MockitoBean
    JwtService jwtService;

    private static final String BASE = "/api/v1/class-sessions";

    // Pre-formatted ISO datetime string used in request bodies
    private static final String FUTURE_DT = "2030-06-01T10:00:00";

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken adminAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "admin@test.com", UserRole.ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken customerAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(2L, "customer@test.com", UserRole.CUSTOMER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    // ---------------------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private ClassSessionResponse sessionResponse(Long id) {
        return new ClassSessionResponse(
                id,
                new ClassTypeSummary(10L, "Spinning"),
                new GymSummary(5L, "Downtown Gym", "Madrid"),
                new InstructorSummary(3L, "John Doe", "Spinning"),
                LocalDateTime.now().plusDays(1),
                45, 20, "A1",
                SessionStatus.SCHEDULED,
                3, 17
        );
    }

    private String validRequestJson() {
        return """
                {
                  "classTypeId": 10,
                  "gymId": 5,
                  "instructorId": 3,
                  "startTime": "%s",
                  "durationMinutes": 45,
                  "maxCapacity": 20,
                  "room": "A1"
                }
                """.formatted(FUTURE_DT);
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/class-sessions — createSession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/class-sessions — createSession")
    class CreateSession {

        @Test
        @DisplayName("admin creates session — returns 201 with Location header and body")
        void createSession_AdminValidRequest_Returns201WithLocationAndBody() throws Exception {
            when(classSessionService.createSession(any())).thenReturn(sessionResponse(100L));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/100"))
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.classType.name").value("Spinning"))
                    .andExpect(jsonPath("$.status").value("SCHEDULED"))
                    .andExpect(jsonPath("$.confirmedCount").value(3))
                    .andExpect(jsonPath("$.availableSpots").value(17));
        }

        @Test
        @DisplayName("missing classTypeId returns 400")
        void createSession_MissingClassTypeId_Returns400() throws Exception {
            String body = """
                    {"startTime": "2030-01-01T10:00:00", "durationMinutes": 45, "maxCapacity": 20, "room": "A1"}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("non-ADMIN user is forbidden — returns 403")
        void createSession_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void createSession_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("classType not found — returns 404 with ClassTypeNotFound error")
        void createSession_ClassTypeNotFound_Returns404() throws Exception {
            when(classSessionService.createSession(any()))
                    .thenThrow(new ClassTypeNotFoundException(999L));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ClassTypeNotFound"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-sessions/{id} — getSessionById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-sessions/{id} — getSessionById")
    class GetSessionById {

        @Test
        @DisplayName("authenticated user retrieves session — returns 200 with body")
        void getSessionById_SessionExists_Returns200() throws Exception {
            when(classSessionService.getSessionById(100L)).thenReturn(sessionResponse(100L));

            mvc.perform(get(BASE + "/100").with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.classType.name").value("Spinning"))
                    .andExpect(jsonPath("$.gym.city").value("Madrid"));
        }

        @Test
        @DisplayName("session not found — returns 404")
        void getSessionById_NotFound_Returns404() throws Exception {
            when(classSessionService.getSessionById(999L)).thenThrow(new SessionNotFoundException(999L));

            mvc.perform(get(BASE + "/999").with(authentication(customerAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getSessionById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/100"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-sessions/schedule — getSchedule
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-sessions/schedule — getSchedule")
    class GetSchedule {

        @Test
        @DisplayName("authenticated user retrieves schedule — returns 200 with list")
        void getSchedule_AuthenticatedUser_Returns200WithList() throws Exception {
            LocalDateTime from = LocalDateTime.now();
            LocalDateTime to = from.plusDays(7);

            when(classSessionService.getSchedule(any(), any()))
                    .thenReturn(List.of(sessionResponse(100L)));

            mvc.perform(get(BASE + "/schedule")
                            .param("from", from.toString())
                            .param("to", to.toString())
                            .with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(100));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getSchedule_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/schedule")
                            .param("from", LocalDateTime.now().toString())
                            .param("to", LocalDateTime.now().plusDays(7).toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-sessions — listSessions
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-sessions — listSessions")
    class ListSessions {

        @Test
        @DisplayName("authenticated user retrieves paginated list — returns 200")
        void listSessions_AuthenticatedUser_Returns200WithPage() throws Exception {
            PageResponse<ClassSessionResponse> page = new PageResponse<>(
                    List.of(sessionResponse(100L)), 0, 10, 1L, 1, false);

            when(classSessionService.listSessions(any(), any())).thenReturn(page);

            mvc.perform(get(BASE).with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(100))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void listSessions_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // PUT /api/v1/class-sessions/{id} — updateSession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/class-sessions/{id} — updateSession")
    class UpdateSession {

        @Test
        @DisplayName("admin updates session — returns 200 with updated body")
        void updateSession_AdminValidRequest_Returns200() throws Exception {
            when(classSessionService.updateSession(eq(100L), any())).thenReturn(sessionResponse(100L));

            mvc.perform(put(BASE + "/100")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100));
        }

        @Test
        @DisplayName("non-ADMIN user is forbidden — returns 403")
        void updateSession_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(put(BASE + "/100")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("updating CANCELLED session — returns 409 with SessionNotBookable error")
        void updateSession_CancelledSession_Returns409() throws Exception {
            when(classSessionService.updateSession(eq(101L), any()))
                    .thenThrow(new SessionNotBookableException(101L));

            mvc.perform(put(BASE + "/101")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SessionNotBookable"));
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/class-sessions/{id}/cancel — cancelSession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/class-sessions/{id}/cancel — cancelSession")
    class CancelSession {

        @Test
        @DisplayName("admin cancels session — returns 204")
        void cancelSession_AdminRequest_Returns204() throws Exception {
            mvc.perform(post(BASE + "/100/cancel").with(authentication(adminAuth())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("non-ADMIN user is forbidden — returns 403")
        void cancelSession_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE + "/100/cancel").with(authentication(customerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("session not found — returns 404")
        void cancelSession_SessionNotFound_Returns404() throws Exception {
            doThrow(new SessionNotFoundException(999L))
                    .when(classSessionService).cancelSession(999L);

            mvc.perform(post(BASE + "/999/cancel").with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("already CANCELLED session — returns 409 with SessionNotBookable error")
        void cancelSession_AlreadyCancelled_Returns409() throws Exception {
            doThrow(new SessionNotBookableException(101L))
                    .when(classSessionService).cancelSession(101L);

            mvc.perform(post(BASE + "/101/cancel").with(authentication(adminAuth())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SessionNotBookable"));
        }
    }
}
