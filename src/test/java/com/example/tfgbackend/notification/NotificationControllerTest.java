package com.example.tfgbackend.notification;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.NotificationNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.NotificationType;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.notification.dto.MarkReadResponse;
import com.example.tfgbackend.notification.dto.NotificationResponse;
import com.example.tfgbackend.notification.dto.UnreadCountResponse;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link NotificationController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 */
@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class NotificationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    JwtService jwtService;

    private static final String NOTIFICATIONS_BASE = "/api/v1/notifications";
    private static final String SESSIONS_BASE = "/api/v1/class-sessions";

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "customer@test.com", UserRole.CUSTOMER);
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
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private NotificationResponse aNotificationResponse(Long id, Long userId) {
        return new NotificationResponse(
                id,
                NotificationType.CONFIRMATION,
                Instant.parse("2026-01-01T10:00:00Z"),
                true,
                Instant.parse("2026-01-01T10:00:00Z"),
                false,
                userId,
                100L);
    }

    private PageResponse<NotificationResponse> singlePage(NotificationResponse item) {
        return new PageResponse<>(List.of(item), 0, 10, 1L, 1, false);
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/notifications/me
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/notifications/me — getMyNotifications")
    class GetMyNotifications {

        @Test
        @DisplayName("authenticated user receives paginated inbox — 200")
        void getMyNotifications_Authenticated_Returns200WithPage() throws Exception {
            PageResponse<NotificationResponse> page = singlePage(aNotificationResponse(1L, 2L));
            when(notificationService.getMyNotifications(eq(2L), any(), eq(false), eq(true), any()))
                    .thenReturn(page);

            mvc.perform(get(NOTIFICATIONS_BASE + "/me")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].type").value("CONFIRMATION"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("unreadOnly filter is forwarded to the service — 200")
        void getMyNotifications_WithUnreadOnlyFilter_ForwardsToService() throws Exception {
            PageResponse<NotificationResponse> page = new PageResponse<>(List.of(), 0, 10, 0L, 0, false);
            when(notificationService.getMyNotifications(eq(2L), any(), eq(true), eq(true), any()))
                    .thenReturn(page);

            mvc.perform(get(NOTIFICATIONS_BASE + "/me")
                            .param("unreadOnly", "true")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("type filter is forwarded to the service — 200")
        void getMyNotifications_WithTypeFilter_ForwardsToService() throws Exception {
            PageResponse<NotificationResponse> page = new PageResponse<>(List.of(), 0, 10, 0L, 0, false);
            when(notificationService.getMyNotifications(eq(2L), eq(NotificationType.REMINDER), eq(false), eq(true), any()))
                    .thenReturn(page);

            mvc.perform(get(NOTIFICATIONS_BASE + "/me")
                            .param("type", "REMINDER")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getMyNotifications_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(NOTIFICATIONS_BASE + "/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/notifications/me/unread-count
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/notifications/me/unread-count — getUnreadCount")
    class GetUnreadCount {

        @Test
        @DisplayName("authenticated user receives unread count — 200")
        void getUnreadCount_Authenticated_Returns200WithCount() throws Exception {
            when(notificationService.getUnreadCount(2L)).thenReturn(new UnreadCountResponse(5L));

            mvc.perform(get(NOTIFICATIONS_BASE + "/me/unread-count")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unread").value(5));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getUnreadCount_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(NOTIFICATIONS_BASE + "/me/unread-count"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/notifications/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/notifications/{id} — getById")
    class GetById {

        @Test
        @DisplayName("owner retrieves own notification — 200")
        void getById_Owner_Returns200() throws Exception {
            when(notificationService.getById(10L, 2L, false))
                    .thenReturn(aNotificationResponse(10L, 2L));

            mvc.perform(get(NOTIFICATIONS_BASE + "/10")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.userId").value(2))
                    .andExpect(jsonPath("$.type").value("CONFIRMATION"));
        }

        @Test
        @DisplayName("admin retrieves any notification — 200")
        void getById_Admin_Returns200() throws Exception {
            when(notificationService.getById(10L, 99L, true))
                    .thenReturn(aNotificationResponse(10L, 2L));

            mvc.perform(get(NOTIFICATIONS_BASE + "/10")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("non-owner gets 404 via NotificationNotFoundException")
        void getById_NonOwner_Returns404() throws Exception {
            when(notificationService.getById(10L, 3L, false))
                    .thenThrow(new NotificationNotFoundException(10L));

            mvc.perform(get(NOTIFICATIONS_BASE + "/10")
                            .with(authentication(customerAuth(3L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NotificationNotFound"));
        }

        @Test
        @DisplayName("notification not found — 404")
        void getById_NotFound_Returns404() throws Exception {
            when(notificationService.getById(eq(999L), any(), anyBoolean()))
                    .thenThrow(new NotificationNotFoundException(999L));

            mvc.perform(get(NOTIFICATIONS_BASE + "/999")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NotificationNotFound"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(NOTIFICATIONS_BASE + "/10"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/notifications/{id}/read
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/notifications/{id}/read — markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("owner marks notification as read — 200 with updated=1")
        void markAsRead_Owner_Returns200WithUpdated1() throws Exception {
            when(notificationService.markAsRead(10L, 2L, false))
                    .thenReturn(new MarkReadResponse(1));

            mvc.perform(post(NOTIFICATIONS_BASE + "/10/read")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(1));
        }

        @Test
        @DisplayName("already-read notification — 200 with updated=0")
        void markAsRead_AlreadyRead_Returns200WithUpdated0() throws Exception {
            when(notificationService.markAsRead(10L, 2L, false))
                    .thenReturn(new MarkReadResponse(0));

            mvc.perform(post(NOTIFICATIONS_BASE + "/10/read")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(0));
        }

        @Test
        @DisplayName("admin marks any notification as read — 200")
        void markAsRead_Admin_Returns200() throws Exception {
            when(notificationService.markAsRead(10L, 99L, true))
                    .thenReturn(new MarkReadResponse(1));

            mvc.perform(post(NOTIFICATIONS_BASE + "/10/read")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(1));
        }

        @Test
        @DisplayName("notification not found — 404")
        void markAsRead_NotFound_Returns404() throws Exception {
            when(notificationService.markAsRead(eq(999L), any(), anyBoolean()))
                    .thenThrow(new NotificationNotFoundException(999L));

            mvc.perform(post(NOTIFICATIONS_BASE + "/999/read")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NotificationNotFound"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void markAsRead_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(NOTIFICATIONS_BASE + "/10/read"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/notifications/me/read-all
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/notifications/me/read-all — markAllAsRead")
    class MarkAllAsRead {

        @Test
        @DisplayName("authenticated user marks all as read — 200 with updated count")
        void markAllAsRead_Authenticated_Returns200WithCount() throws Exception {
            when(notificationService.markAllAsRead(2L)).thenReturn(new MarkReadResponse(3));

            mvc.perform(post(NOTIFICATIONS_BASE + "/me/read-all")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(3));
        }

        @Test
        @DisplayName("no unread notifications — 200 with updated=0")
        void markAllAsRead_NoneUnread_Returns200WithZero() throws Exception {
            when(notificationService.markAllAsRead(2L)).thenReturn(new MarkReadResponse(0));

            mvc.perform(post(NOTIFICATIONS_BASE + "/me/read-all")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(0));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void markAllAsRead_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(NOTIFICATIONS_BASE + "/me/read-all"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/notifications/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/notifications/{id} — delete")
    class Delete {

        @Test
        @DisplayName("owner deletes their notification — 204")
        void delete_Owner_Returns204() throws Exception {
            mvc.perform(delete(NOTIFICATIONS_BASE + "/10")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isNoContent());

            verify(notificationService).delete(10L, 2L, false);
        }

        @Test
        @DisplayName("admin deletes any notification — 204")
        void delete_Admin_Returns204() throws Exception {
            mvc.perform(delete(NOTIFICATIONS_BASE + "/10")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());

            verify(notificationService).delete(10L, 99L, true);
        }

        @Test
        @DisplayName("notification not found — 404")
        void delete_NotFound_Returns404() throws Exception {
            doThrow(new NotificationNotFoundException(999L))
                    .when(notificationService).delete(eq(999L), any(), anyBoolean());

            mvc.perform(delete(NOTIFICATIONS_BASE + "/999")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NotificationNotFound"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void delete_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(NOTIFICATIONS_BASE + "/10"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/notifications/users/{userId}  (ADMIN only)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/notifications/users/{userId} — getUserNotifications (ADMIN only)")
    class GetUserNotifications {

        @Test
        @DisplayName("ADMIN retrieves paginated notifications for a user — 200")
        void getUserNotifications_Admin_Returns200WithPage() throws Exception {
            PageResponse<NotificationResponse> page = singlePage(aNotificationResponse(1L, 5L));
            when(notificationService.getUserNotifications(eq(5L), any(), any())).thenReturn(page);

            mvc.perform(get(NOTIFICATIONS_BASE + "/users/5")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("ADMIN filters by type — 200")
        void getUserNotifications_AdminWithTypeFilter_Returns200() throws Exception {
            PageResponse<NotificationResponse> page = new PageResponse<>(List.of(), 0, 10, 0L, 0, false);
            when(notificationService.getUserNotifications(eq(5L), eq(NotificationType.CANCELLATION), any()))
                    .thenReturn(page);

            mvc.perform(get(NOTIFICATIONS_BASE + "/users/5")
                            .param("type", "CANCELLATION")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CUSTOMER is forbidden — 403")
        void getUserNotifications_Customer_Returns403() throws Exception {
            mvc.perform(get(NOTIFICATIONS_BASE + "/users/5")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("INSTRUCTOR is forbidden — 403")
        void getUserNotifications_Instructor_Returns403() throws Exception {
            mvc.perform(get(NOTIFICATIONS_BASE + "/users/5")
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getUserNotifications_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(NOTIFICATIONS_BASE + "/users/5"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-sessions/{sessionId}/notifications  (INSTRUCTOR or ADMIN)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-sessions/{sessionId}/notifications — getSessionNotifications (INSTRUCTOR or ADMIN)")
    class GetSessionNotifications {

        @Test
        @DisplayName("INSTRUCTOR retrieves session notifications — 200")
        void getSessionNotifications_Instructor_Returns200WithList() throws Exception {
            when(notificationService.getSessionNotifications(100L))
                    .thenReturn(List.of(aNotificationResponse(1L, 2L)));

            mvc.perform(get(SESSIONS_BASE + "/100/notifications")
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].type").value("CONFIRMATION"));
        }

        @Test
        @DisplayName("ADMIN retrieves session notifications — 200")
        void getSessionNotifications_Admin_Returns200WithList() throws Exception {
            when(notificationService.getSessionNotifications(100L))
                    .thenReturn(List.of(aNotificationResponse(1L, 2L)));

            mvc.perform(get(SESSIONS_BASE + "/100/notifications")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("CUSTOMER is forbidden — 403")
        void getSessionNotifications_Customer_Returns403() throws Exception {
            mvc.perform(get(SESSIONS_BASE + "/100/notifications")
                            .with(authentication(customerAuth(2L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("session not found — 404")
        void getSessionNotifications_SessionNotFound_Returns404() throws Exception {
            when(notificationService.getSessionNotifications(999L))
                    .thenThrow(new SessionNotFoundException(999L));

            mvc.perform(get(SESSIONS_BASE + "/999/notifications")
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getSessionNotifications_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(SESSIONS_BASE + "/100/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
