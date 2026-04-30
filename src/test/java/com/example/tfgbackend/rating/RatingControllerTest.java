package com.example.tfgbackend.rating;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.NotAttendedSessionException;
import com.example.tfgbackend.common.exception.RatingAlreadyExistsException;
import com.example.tfgbackend.common.exception.RatingNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.rating.dto.RatingResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link RatingController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 */
@WebMvcTest(RatingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class RatingControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RatingService ratingService;

    @MockitoBean
    JwtService jwtService;

    private static final String BASE = "/api/v1/ratings";

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

    private RatingResponse ratingResponse(Long id) {
        return new RatingResponse(id, 4, "Great class", Instant.parse("2026-01-01T10:00:00Z"), 2L, 10L);
    }

    private PageResponse<RatingResponse> singlePageResponse(RatingResponse rating) {
        return new PageResponse<>(List.of(rating), 0, 10, 1L, 1, false);
    }

    private String validCreateJson() {
        return """
                {
                  "sessionId": 10,
                  "score": 4,
                  "comment": "Great class"
                }
                """;
    }

    private String validUpdateJson() {
        return """
                {
                  "score": 5,
                  "comment": "Excellent!"
                }
                """;
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/ratings — create
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/ratings — create")
    class Create {

        @Test
        @DisplayName("CUSTOMER submits valid rating — 201 with Location header and body")
        void create_CustomerValidRequest_Returns201WithLocationAndBody() throws Exception {
            when(ratingService.create(eq(2L), any())).thenReturn(ratingResponse(5L));

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/5"))
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.score").value(4))
                    .andExpect(jsonPath("$.comment").value("Great class"))
                    .andExpect(jsonPath("$.userId").value(2))
                    .andExpect(jsonPath("$.sessionId").value(10));
        }

        @Test
        @DisplayName("non-CUSTOMER (ADMIN) — returns 403")
        void create_AdminForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated — returns 401")
        void create_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("missing sessionId — returns 400 with ValidationFailed error")
        void create_MissingSessionId_Returns400() throws Exception {
            String body = """
                    {
                      "score": 4,
                      "comment": "Great class"
                    }
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("score out of range (6) — returns 400 with ValidationFailed error")
        void create_ScoreOutOfRange_Returns400() throws Exception {
            String body = """
                    {
                      "sessionId": 10,
                      "score": 6,
                      "comment": "Too high"
                    }
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("score below minimum (0) — returns 400 with ValidationFailed error")
        void create_ScoreBelowMinimum_Returns400() throws Exception {
            String body = """
                    {
                      "sessionId": 10,
                      "score": 0,
                      "comment": "Too low"
                    }
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("session not found — returns 404 with SessionNotFound error")
        void create_SessionNotFound_Returns404() throws Exception {
            when(ratingService.create(eq(2L), any()))
                    .thenThrow(new SessionNotFoundException(10L));

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("user has not attended session — returns 409 with NotAttendedSession error")
        void create_NotAttended_Returns409() throws Exception {
            when(ratingService.create(eq(2L), any()))
                    .thenThrow(new NotAttendedSessionException(2L, 10L));

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("NotAttendedSession"));
        }

        @Test
        @DisplayName("duplicate rating — returns 409 with RatingAlreadyExists error")
        void create_DuplicateRating_Returns409() throws Exception {
            when(ratingService.create(eq(2L), any()))
                    .thenThrow(new RatingAlreadyExistsException(2L, 10L));

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("RatingAlreadyExists"));
        }
    }

    // ---------------------------------------------------------------------------
    // PUT /api/v1/ratings/{id} — update
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/ratings/{id} — update")
    class Update {

        @Test
        @DisplayName("CUSTOMER updates own rating — returns 200 with updated body")
        void update_CustomerValidRequest_Returns200WithBody() throws Exception {
            RatingResponse updated = new RatingResponse(1L, 5, "Excellent!", Instant.parse("2026-01-02T10:00:00Z"), 2L, 10L);
            when(ratingService.update(eq(2L), eq(1L), any())).thenReturn(updated);

            mvc.perform(put(BASE + "/1")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.score").value(5))
                    .andExpect(jsonPath("$.comment").value("Excellent!"));
        }

        @Test
        @DisplayName("non-CUSTOMER (ADMIN) — returns 403")
        void update_AdminForbidden_Returns403() throws Exception {
            mvc.perform(put(BASE + "/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated — returns 401")
        void update_Unauthenticated_Returns401() throws Exception {
            mvc.perform(put(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rating not found or not owner — returns 404 with RatingNotFound error")
        void update_NotFoundOrNotOwner_Returns404() throws Exception {
            when(ratingService.update(eq(2L), eq(999L), any()))
                    .thenThrow(new RatingNotFoundException(999L));

            mvc.perform(put(BASE + "/999")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validUpdateJson()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RatingNotFound"));
        }

        @Test
        @DisplayName("invalid score (0) — returns 400 with ValidationFailed error")
        void update_InvalidScore_Returns400() throws Exception {
            String invalidBody = """
                    {
                      "score": 0,
                      "comment": "Too low"
                    }
                    """;

            mvc.perform(put(BASE + "/1")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("score too high (6) — returns 400 with ValidationFailed error")
        void update_ScoreTooHigh_Returns400() throws Exception {
            String invalidBody = """
                    {
                      "score": 6,
                      "comment": "Out of bounds"
                    }
                    """;

            mvc.perform(put(BASE + "/1")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("comment exceeds max size — returns 400 with ValidationFailed error")
        void update_CommentTooLong_Returns400() throws Exception {
            String longComment = "x".repeat(1001);
            String invalidBody = String.format("""
                    {
                      "score": 4,
                      "comment": "%s"
                    }
                    """, longComment);

            mvc.perform(put(BASE + "/1")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/ratings/{id} — delete
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/ratings/{id} — delete")
    class DeleteRating {

        @Test
        @DisplayName("ADMIN deletes any rating — returns 204")
        void delete_AdminDeletesAny_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/1").with(authentication(adminAuth())))
                    .andExpect(status().isNoContent());

            verify(ratingService).delete(1L, UserRole.ADMIN, 1L);
        }

        @Test
        @DisplayName("CUSTOMER deletes own rating — returns 204")
        void delete_CustomerDeletesOwn_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/1").with(authentication(customerAuth())))
                    .andExpect(status().isNoContent());

            verify(ratingService).delete(2L, UserRole.CUSTOMER, 1L);
        }

        @Test
        @DisplayName("unauthenticated — returns 401")
        void delete_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rating not found — returns 404 with RatingNotFound error")
        void delete_NotFound_Returns404() throws Exception {
            doThrow(new RatingNotFoundException(999L)).when(ratingService).delete(2L, UserRole.CUSTOMER, 999L);

            mvc.perform(delete(BASE + "/999").with(authentication(customerAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RatingNotFound"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/ratings/session/{sessionId} — listBySession
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/ratings/session/{sessionId} — listBySession")
    class ListBySession {

        @Test
        @DisplayName("authenticated user — returns 200 with paginated body")
        void listBySession_Authenticated_Returns200WithPage() throws Exception {
            when(ratingService.listBySession(eq(10L), any()))
                    .thenReturn(singlePageResponse(ratingResponse(1L)));

            mvc.perform(get(BASE + "/session/10").with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].score").value(4))
                    .andExpect(jsonPath("$.content[0].comment").value("Great class"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("unauthenticated — returns 401")
        void listBySession_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/session/10"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("session not found — returns 404 with SessionNotFound error")
        void listBySession_SessionNotFound_Returns404() throws Exception {
            when(ratingService.listBySession(eq(999L), any()))
                    .thenThrow(new SessionNotFoundException(999L));

            mvc.perform(get(BASE + "/session/999").with(authentication(customerAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/ratings/me — myRatings
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/ratings/me — myRatings")
    class MyRatings {

        @Test
        @DisplayName("authenticated user — returns 200 with paginated body")
        void myRatings_Authenticated_Returns200WithPage() throws Exception {
            when(ratingService.myRatings(eq(2L), any()))
                    .thenReturn(singlePageResponse(ratingResponse(1L)));

            mvc.perform(get(BASE + "/me").with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("unauthenticated — returns 401")
        void myRatings_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/me"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
