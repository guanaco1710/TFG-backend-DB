package com.example.tfgbackend.auth;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.ForgotPasswordRequest;
import com.example.tfgbackend.auth.dto.ForgotPasswordResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RefreshRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
import com.example.tfgbackend.auth.dto.ResetPasswordRequest;
import com.example.tfgbackend.auth.dto.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end integration test for the auth flow.
 *
 * <p>Uses a real PostgreSQL container (via Testcontainers), a real Spring Security filter chain,
 * and a real HTTP server on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("app.jwt.secret", () -> "integration-test-secret-must-be-at-least-32-bytes-long!!");
        registry.add("app.jwt.access-token-expiration", () -> "900");
        registry.add("app.jwt.refresh-token-expiration", () -> "604800");
    }

    @LocalServerPort
    int port;

    RestTemplate rest;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate(new SimpleClientHttpRequestFactory());
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static final String BASE = "/api/v1/auth";

    @Test
    @DisplayName("register → login → refresh → access protected endpoint")
    void fullAuthFlow_RegisterLoginRefreshProtected() {
        RegisterRequest reg = new RegisterRequest("Integration User", "integ@test.com", "supersecret123!", null);
        ResponseEntity<AuthResponse> regResp = rest.postForEntity(url(BASE + "/register"), reg, AuthResponse.class);

        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse regBody = regResp.getBody();
        assertThat(regBody).isNotNull();
        assertThat(regBody.tokens().accessToken()).isNotBlank();
        assertThat(regBody.tokens().refreshToken()).isNotBlank();
        assertThat(regBody.user().email()).isEqualTo("integ@test.com");

        LoginRequest login = new LoginRequest("integ@test.com", "supersecret123!");
        ResponseEntity<AuthResponse> loginResp = rest.postForEntity(url(BASE + "/login"), login, AuthResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse loginBody = loginResp.getBody();
        assertThat(loginBody).isNotNull();
        assertThat(loginBody.tokens().accessToken()).isNotBlank();
        String refreshToken = loginBody.tokens().refreshToken();

        RefreshRequest refreshReq = new RefreshRequest(refreshToken);
        ResponseEntity<TokenPair> refreshResp = rest.postForEntity(url(BASE + "/refresh"), refreshReq, TokenPair.class);

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenPair refreshBody = refreshResp.getBody();
        assertThat(refreshBody).isNotNull();
        assertThat(refreshBody.accessToken()).isNotBlank();
        String newAccessToken = refreshBody.accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(newAccessToken);
        ResponseEntity<String> protectedResp = rest.exchange(
                url("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(protectedResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("register: duplicate email returns 409")
    void register_DuplicateEmail_Returns409() {
        RegisterRequest reg = new RegisterRequest("Dup User", "dup@test.com", "supersecret123!", null);
        rest.postForEntity(url(BASE + "/register"), reg, AuthResponse.class);

        ResponseEntity<String> second = rest.postForEntity(url(BASE + "/register"), reg, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("login: wrong password returns 401")
    void login_WrongPassword_Returns401() {
        RegisterRequest reg = new RegisterRequest("Wrong User", "wrong@test.com", "supersecret123!", null);
        rest.postForEntity(url(BASE + "/register"), reg, AuthResponse.class);

        LoginRequest bad = new LoginRequest("wrong@test.com", "incorrect-password");
        ResponseEntity<String> resp = rest.postForEntity(url(BASE + "/login"), bad, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh: revoked token returns 401")
    void refresh_RevokedToken_Returns401() {
        RegisterRequest reg = new RegisterRequest("Revoke User", "revoke@test.com", "supersecret123!", null);
        ResponseEntity<AuthResponse> regResp = rest.postForEntity(url(BASE + "/register"), reg, AuthResponse.class);
        String firstRefresh = regResp.getBody().tokens().refreshToken();

        RefreshRequest first = new RefreshRequest(firstRefresh);
        rest.postForEntity(url(BASE + "/refresh"), first, TokenPair.class);

        ResponseEntity<String> second = rest.postForEntity(url(BASE + "/refresh"), first, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("protected endpoint without token returns 401")
    void protectedEndpoint_NoToken_Returns401() {
        ResponseEntity<String> resp = rest.getForEntity(url("/api/v1/users/me"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("forgot-password → reset-password → login with new password succeeds")
    void forgotPassword_ThenResetPassword_AllowsLoginWithNewPassword() {
        RegisterRequest reg = new RegisterRequest("Reset User", "resetpw@test.com", "originalpass1!", null);
        rest.postForEntity(url(BASE + "/register"), reg, AuthResponse.class);

        ForgotPasswordResponse forgotResp = rest.postForEntity(
                url(BASE + "/forgot-password"),
                new ForgotPasswordRequest("resetpw@test.com"),
                ForgotPasswordResponse.class).getBody();

        assertThat(forgotResp).isNotNull();
        assertThat(forgotResp.resetToken()).isNotBlank();

        rest.postForEntity(
                url(BASE + "/reset-password"),
                new ResetPasswordRequest(forgotResp.resetToken(), "newpassword99!"),
                Void.class);

        ResponseEntity<AuthResponse> loginResp = rest.postForEntity(
                url(BASE + "/login"),
                new LoginRequest("resetpw@test.com", "newpassword99!"),
                AuthResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody().tokens().accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("forgot-password: unknown email still returns 200 without token")
    void forgotPassword_UnknownEmail_Returns200WithoutToken() {
        ResponseEntity<ForgotPasswordResponse> resp = rest.postForEntity(
                url(BASE + "/forgot-password"),
                new ForgotPasswordRequest("ghost@test.com"),
                ForgotPasswordResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().resetToken()).isNull();
    }

    @Test
    @DisplayName("reset-password: invalid token returns 401")
    void resetPassword_InvalidToken_Returns401() {
        ResponseEntity<String> resp = rest.postForEntity(
                url(BASE + "/reset-password"),
                new ResetPasswordRequest("totally-fake-token", "newpassword99!"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("reset-password: reusing a token returns 401")
    void resetPassword_ReuseToken_Returns401() {
        RegisterRequest reg = new RegisterRequest("Reuse User", "reuse@test.com", "originalpass1!", null);
        rest.postForEntity(url(BASE + "/register"), reg, AuthResponse.class);

        String token = rest.postForEntity(
                url(BASE + "/forgot-password"),
                new ForgotPasswordRequest("reuse@test.com"),
                ForgotPasswordResponse.class).getBody().resetToken();

        rest.postForEntity(url(BASE + "/reset-password"),
                new ResetPasswordRequest(token, "newpassword99!"), Void.class);

        ResponseEntity<String> second = rest.postForEntity(
                url(BASE + "/reset-password"),
                new ResetPasswordRequest(token, "anotherpass99!"),
                String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
