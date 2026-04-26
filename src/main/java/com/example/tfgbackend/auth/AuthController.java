package com.example.tfgbackend.auth;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.ForgotPasswordRequest;
import com.example.tfgbackend.auth.dto.ForgotPasswordResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RefreshRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
import com.example.tfgbackend.auth.dto.ResetPasswordRequest;
import com.example.tfgbackend.auth.dto.TokenPair;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints — all public (no Bearer token required).
 *
 * <p>Business logic lives entirely in {@link AuthService}; this class only
 * validates input and maps service results to HTTP responses.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new gym member account.
     *
     * @return 201 Created with access + refresh tokens and user summary
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user by email and password.
     *
     * @return 200 OK with access + refresh tokens and user summary
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Exchanges a valid refresh token for a new access + refresh pair (rotation).
     *
     * @return 200 OK with the new token pair
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
