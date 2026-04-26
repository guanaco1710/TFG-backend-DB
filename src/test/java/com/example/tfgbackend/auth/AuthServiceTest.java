package com.example.tfgbackend.auth;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RefreshRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
import com.example.tfgbackend.auth.dto.TokenPair;
import com.example.tfgbackend.common.exception.EmailAlreadyExistsException;
import com.example.tfgbackend.common.exception.InvalidCredentialsException;
import com.example.tfgbackend.common.exception.TokenExpiredException;
import com.example.tfgbackend.common.exception.TokenRevokedException;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AuthService}. No Spring context — all collaborators mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    @InjectMocks AuthService authService;

    // -----------------------------------------------------------------------
    // register
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register: new user is saved and tokens are returned")
    void register_NewUser_SavesUserAndReturnsTokens() {
        RegisterRequest req = new RegisterRequest("Alice", "alice@test.com", "supersecret123", null);
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("supersecret123")).thenReturn("$2a$12$hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return reflectId(u, 1L);
        });
        when(jwtService.generateAccessToken(any())).thenReturn("access.jwt");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(req);

        assertThat(response.tokens().accessToken()).isEqualTo("access.jwt");
        assertThat(response.tokens().refreshToken()).isEqualTo("raw-refresh");
        assertThat(response.user().email()).isEqualTo("alice@test.com");
        assertThat(response.user().role()).isEqualTo(UserRole.CUSTOMER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@test.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$12$hash");

        verify(refreshTokenRepository).save(any());
    }

    @Test
    @DisplayName("register: default role is CUSTOMER when not supplied")
    void register_NoRoleProvided_DefaultsToCustomer() {
        RegisterRequest req = new RegisterRequest("Bob", "bob@test.com", "supersecret456", null);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(userRepository.save(any())).thenAnswer(inv -> reflectId(inv.getArgument(0), 2L));
        when(jwtService.generateAccessToken(any())).thenReturn("tok");
        when(jwtService.generateRefreshToken()).thenReturn("ref");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse resp = authService.register(req);

        assertThat(resp.user().role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("register: duplicate email throws EmailAlreadyExistsException")
    void register_DuplicateEmail_ThrowsEmailAlreadyExistsException() {
        RegisterRequest req = new RegisterRequest("Alice", "alice@test.com", "supersecret123", null);
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // login
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login: correct credentials return tokens")
    void login_CorrectCredentials_ReturnsTokens() {
        User user = buildUser(1L, "alice@test.com", "$2a$12$hash", UserRole.CUSTOMER);
        LoginRequest req = new LoginRequest("alice@test.com", "supersecret123");

        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret123", "$2a$12$hash")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access.jwt");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse resp = authService.login(req);

        assertThat(resp.tokens().accessToken()).isEqualTo("access.jwt");
        assertThat(resp.user().email()).isEqualTo("alice@test.com");
    }

    @Test
    @DisplayName("login: unknown email throws InvalidCredentialsException")
    void login_UnknownEmail_ThrowsInvalidCredentialsException() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@test.com", "pass")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("login: wrong password throws InvalidCredentialsException")
    void login_WrongPassword_ThrowsInvalidCredentialsException() {
        User user = buildUser(1L, "alice@test.com", "$2a$12$hash", UserRole.CUSTOMER);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@test.com", "wrongpass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -----------------------------------------------------------------------
    // refresh
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("refresh: valid token returns new token pair and rotates the refresh token")
    void refresh_ValidToken_ReturnsNewTokenPairAndRotates() {
        String rawToken = "raw-refresh-token";
        String tokenHash = JwtService.sha256Hex(rawToken);

        User user = buildUser(1L, "alice@test.com", "$2a$12$hash", UserRole.CUSTOMER);
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(tokenHash)
                .userId(1L)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new.access.jwt");
        when(jwtService.generateRefreshToken()).thenReturn("new-raw-refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenPair pair = authService.refresh(new RefreshRequest(rawToken));

        assertThat(pair.accessToken()).isEqualTo("new.access.jwt");
        assertThat(pair.refreshToken()).isEqualTo("new-raw-refresh");

        // Old token must be revoked
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored); // saving the revoked old token
    }

    @Test
    @DisplayName("refresh: expired token throws TokenExpiredException")
    void refresh_ExpiredToken_ThrowsTokenExpiredException() {
        String rawToken = "expired-token";
        String tokenHash = JwtService.sha256Hex(rawToken);

        RefreshToken stored = RefreshToken.builder()
                .tokenHash(tokenHash)
                .userId(1L)
                .expiresAt(Instant.now().minusSeconds(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("refresh: revoked token throws TokenRevokedException")
    void refresh_RevokedToken_ThrowsTokenRevokedException() {
        String rawToken = "revoked-token";
        String tokenHash = JwtService.sha256Hex(rawToken);

        RefreshToken stored = RefreshToken.builder()
                .tokenHash(tokenHash)
                .userId(1L)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOf(TokenRevokedException.class);
    }

    @Test
    @DisplayName("refresh: unknown token throws TokenRevokedException")
    void refresh_UnknownToken_ThrowsTokenRevokedException() {
        String rawToken = "unknown-token";
        String tokenHash = JwtService.sha256Hex(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOf(TokenRevokedException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private User buildUser(Long id, String email, String hash, UserRole role) {
        User u = User.builder()
                .name("Test User")
                .email(email)
                .passwordHash(hash)
                .role(role)
                .active(true)
                .build();
        return reflectId(u, id);
    }

    /**
     * Reflectively sets the {@code id} field inherited from {@link com.example.tfgbackend.common.BaseEntity}.
     * Needed because the field is private and has no setter.
     */
    private User reflectId(User user, Long id) {
        try {
            var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
