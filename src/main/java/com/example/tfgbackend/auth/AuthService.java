package com.example.tfgbackend.auth;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RefreshRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
import com.example.tfgbackend.auth.dto.TokenPair;
import com.example.tfgbackend.auth.dto.UserSummary;
import com.example.tfgbackend.common.exception.EmailAlreadyExistsException;
import com.example.tfgbackend.common.exception.InvalidCredentialsException;
import com.example.tfgbackend.common.exception.TokenExpiredException;
import com.example.tfgbackend.common.exception.TokenRevokedException;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles registration, login, and token refresh for the auth flow.
 *
 * <p>Write operations are @Transactional to ensure atomicity (e.g. user + refresh token
 * creation succeed or fail together).
 *
 * <p>Security note: login and registration errors use identical messages to prevent
 * user enumeration.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Registers a new user account (default role: CUSTOMER) and returns tokens.
     *
     * @param request registration details
     * @return auth response containing access + refresh tokens and user summary
     * @throws EmailAlreadyExistsException if the email is already taken
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        UserRole role = request.role() != null ? request.role() : UserRole.CUSTOMER;

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .active(true)
                .build();
        user = userRepository.save(user);

        TokenPair tokens = issueTokenPair(user);
        return new AuthResponse(tokens, toSummary(user));
    }

    /**
     * Authenticates a user by email and password, returning tokens.
     *
     * @param request login credentials
     * @return auth response containing access + refresh tokens and user summary
     * @throws InvalidCredentialsException if email is not found or password doesn't match
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        TokenPair tokens = issueTokenPair(user);
        return new AuthResponse(tokens, toSummary(user));
    }

    /**
     * Rotates a refresh token: validates the old one, issues a new pair, and revokes the old token.
     *
     * @param request the current refresh token value (raw, not hashed)
     * @return a new token pair
     * @throws TokenRevokedException if the token is unknown or already revoked
     * @throws TokenExpiredException if the token has passed its expiry
     */
    @Transactional
    public TokenPair refresh(RefreshRequest request) {
        String hash = JwtService.sha256Hex(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(TokenRevokedException::new);

        if (stored.isRevoked()) {
            throw new TokenRevokedException();
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException();
        }

        // Revoke the old token before issuing a new one (rotation)
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(TokenRevokedException::new);

        return issueTokenPair(user);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private TokenPair issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(JwtService.sha256Hex(rawRefresh))
                .userId(user.getId())
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenExpirationSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, rawRefresh);
    }

    private UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
