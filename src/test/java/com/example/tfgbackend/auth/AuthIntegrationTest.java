package com.example.tfgbackend.auth;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RefreshRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
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
}
