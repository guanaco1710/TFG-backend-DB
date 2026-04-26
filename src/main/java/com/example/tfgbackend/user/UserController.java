package com.example.tfgbackend.user;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing endpoints.
 *
 * <p>All routes require a valid Bearer JWT (enforced by {@link com.example.tfgbackend.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Returns the profile of the currently-authenticated user.
     *
     * @param principal injected from the validated JWT
     * @return 200 OK with the user's profile DTO
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.getById(principal.userId()));
    }
}
