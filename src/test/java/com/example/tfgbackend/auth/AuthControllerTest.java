package com.example.tfgbackend.auth;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RefreshRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
import com.example.tfgbackend.auth.dto.TokenPair;
import com.example.tfgbackend.auth.dto.UserSummary;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.exception.EmailAlreadyExistsException;
import com.example.tfgbackend.common.exception.InvalidCredentialsException;
import com.example.tfgbackend.common.exception.TokenExpiredException;
import com.example.tfgbackend.common.exception.TokenRevokedException;
import com.example.tfgbackend.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuthController}.
 *
 * <p>Spring Security is fully active in @WebMvcTest; we must permit /api/v1/auth/** or
 * configure the test security context. We import SecurityConfig so the real permit rules apply.
 */
@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, com.example.tfgbackend.config.SecurityConfig.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean AuthService authService;
    // SecurityConfig needs JwtService to build the filter
    @MockitoBean JwtService jwtService;

    private static final String BASE = "/api/v1/auth";

    private AuthResponse sampleAuthResponse() {
        TokenPair tokens = new TokenPair("access.token.here", "refresh-token-raw");
        UserSummary user = new UserSummary(1L, "Alice", "alice@test.com", UserRole.CUSTOMER);
        return new AuthResponse(tokens, user);
    }

    // -----------------------------------------------------------------------
    // register
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("register: valid request returns 201 with tokens")
    void register_ValidRequest_Returns201WithTokens() throws Exception {
        when(authService.register(any())).thenReturn(sampleAuthResponse());

        RegisterRequest body = new RegisterRequest("Alice", "alice@test.com", "supersecret123", null);

        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokens.accessToken").value("access.token.here"))
                .andExpect(jsonPath("$.tokens.refreshToken").value("refresh-token-raw"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("alice@test.com"));
    }

    @Test
    @DisplayName("register: duplicate email returns 409")
    void register_DuplicateEmail_Returns409() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("alice@test.com"));

        RegisterRequest body = new RegisterRequest("Alice", "alice@test.com", "supersecret123", null);

        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("register: invalid email returns 400")
    void register_InvalidEmail_Returns400() throws Exception {
        RegisterRequest body = new RegisterRequest("Alice", "not-an-email", "supersecret123", null);

        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // login
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login: valid credentials returns 200 with tokens")
    void login_ValidCredentials_Returns200WithTokens() throws Exception {
        when(authService.login(any())).thenReturn(sampleAuthResponse());

        LoginRequest body = new LoginRequest("alice@test.com", "supersecret123");

        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens.accessToken").value("access.token.here"))
                .andExpect(jsonPath("$.user.email").value("alice@test.com"));
    }

    @Test
    @DisplayName("login: wrong password returns 401")
    void login_WrongPassword_Returns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        LoginRequest body = new LoginRequest("alice@test.com", "wrongpassword");

        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("login: unknown email returns 401 (no enumeration)")
    void login_UnknownEmail_Returns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        LoginRequest body = new LoginRequest("nobody@test.com", "anything");

        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // refresh
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("refresh: valid token returns 200 with new tokens")
    void refresh_ValidToken_Returns200WithNewTokens() throws Exception {
        TokenPair newPair = new TokenPair("new.access.token", "new-refresh-token");
        when(authService.refresh(any())).thenReturn(newPair);

        RefreshRequest body = new RefreshRequest("old-refresh-token");

        mvc.perform(post(BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("refresh: expired token returns 401")
    void refresh_ExpiredToken_Returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new TokenExpiredException());

        RefreshRequest body = new RefreshRequest("expired-token");

        mvc.perform(post(BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("refresh: revoked token returns 401")
    void refresh_RevokedToken_Returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new TokenRevokedException());

        RefreshRequest body = new RefreshRequest("revoked-token");

        mvc.perform(post(BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
