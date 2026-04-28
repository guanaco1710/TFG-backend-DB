package com.example.tfgbackend.user;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.dto.AdminUpdateUserRequest;
import com.example.tfgbackend.user.dto.UpdateUserRequest;
import com.example.tfgbackend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing endpoints.
 *
 * <p>All routes require a valid Bearer JWT (enforced by {@link com.example.tfgbackend.config.SecurityConfig}).
 * Admin-only routes are additionally guarded by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Returns the profile of the currently-authenticated user. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.getById(principal.userId()));
    }

    /** Updates the authenticated user's own name and/or phone (partial update). */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @Valid @RequestBody UpdateUserRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.updateMe(principal.userId(), req));
    }

    /** Admin: paginated list of all users, optionally filtered by role. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) UserRole role,
            Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(role, pageable));
    }

    /** Admin: fetch a single user by ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /** Admin: partial update of any user's name, phone, role, or active flag. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> adminUpdateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequest req) {
        return ResponseEntity.ok(userService.adminUpdateUser(id, req));
    }

    /** Admin: soft-delete a user (sets active=false). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}
