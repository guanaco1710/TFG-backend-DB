package com.example.tfgbackend.notification;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.enums.NotificationType;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.notification.dto.MarkReadResponse;
import com.example.tfgbackend.notification.dto.NotificationResponse;
import com.example.tfgbackend.notification.dto.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private static final String NOTIFICATIONS_BASE = "/api/v1/notifications";
    private static final String SESSIONS_BASE = "/api/v1/class-sessions";

    @GetMapping(NOTIFICATIONS_BASE + "/me")
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "true") boolean sentOnly,
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                notificationService.getMyNotifications(principal.userId(), type, unreadOnly, sentOnly, pageable));
    }

    @GetMapping(NOTIFICATIONS_BASE + "/me/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(notificationService.getUnreadCount(principal.userId()));
    }

    @GetMapping(NOTIFICATIONS_BASE + "/{id}")
    public ResponseEntity<NotificationResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean isAdmin = principal.role() == UserRole.ADMIN;
        return ResponseEntity.ok(notificationService.getById(id, principal.userId(), isAdmin));
    }

    @PostMapping(NOTIFICATIONS_BASE + "/{id}/read")
    public ResponseEntity<MarkReadResponse> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean isAdmin = principal.role() == UserRole.ADMIN;
        return ResponseEntity.ok(notificationService.markAsRead(id, principal.userId(), isAdmin));
    }

    @PostMapping(NOTIFICATIONS_BASE + "/me/read-all")
    public ResponseEntity<MarkReadResponse> markAllAsRead(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(notificationService.markAllAsRead(principal.userId()));
    }

    @DeleteMapping(NOTIFICATIONS_BASE + "/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean isAdmin = principal.role() == UserRole.ADMIN;
        notificationService.delete(id, principal.userId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(NOTIFICATIONS_BASE + "/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<NotificationResponse>> getUserNotifications(
            @PathVariable Long userId,
            @RequestParam(required = false) NotificationType type,
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, type, pageable));
    }

    @GetMapping(SESSIONS_BASE + "/{sessionId}/notifications")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<NotificationResponse>> getSessionNotifications(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(notificationService.getSessionNotifications(sessionId));
    }
}
