package com.example.tfgbackend.gym;

import com.example.tfgbackend.auth.dto.AuthResponse;
import com.example.tfgbackend.auth.dto.LoginRequest;
import com.example.tfgbackend.auth.dto.RegisterRequest;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.dto.GymRequest;
import com.example.tfgbackend.gym.dto.GymResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end integration test for the gym management feature.
 *
 * Uses a real PostgreSQL container (Testcontainers), real Spring Security with JWT,
 * and a live HTTP server on a random port.
 *
 * Each test registers its own users to stay independent of test execution order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GymIntegrationTest {

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
        registry.add("app.jwt.secret", () -> "gym-integration-test-secret-must-be-at-least-32-bytes!!");
        registry.add("app.jwt.access-token-expiration", () -> "900");
        registry.add("app.jwt.refresh-token-expiration", () -> "604800");
    }

    @LocalServerPort
    int port;

    RestTemplate rest;
    ObjectMapper objectMapper;

    private static final String AUTH_BASE = "/api/v1/auth";
    private static final String GYMS_BASE = "/api/v1/gyms";

    // Unique suffix per test run to avoid duplicate email conflicts when DDL is create-drop
    private static final String SUFFIX = String.valueOf(System.nanoTime());

    @BeforeEach
    void setUp() {
        rest = new RestTemplate(new SimpleClientHttpRequestFactory());
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for Instant serialisation
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private String registerAndGetToken(String emailPrefix, UserRole role) {
        String email = emailPrefix + "_" + SUFFIX + "@gym-it.test";
        RegisterRequest reg = new RegisterRequest("Test User " + emailPrefix, email, "supersecret123!", role);
        ResponseEntity<AuthResponse> resp = rest.postForEntity(url(AUTH_BASE + "/register"), reg, AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().tokens().accessToken();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpEntity<GymRequest> gymEntity(String token, GymRequest body) {
        return new HttpEntity<>(body, bearerHeaders(token));
    }

    private HttpEntity<Void> voidEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    // ---------------------------------------------------------------------------
    // Integration scenarios
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("ADMIN creates gym → 201 + Location; GET the created gym → 200")
    void adminCreatesGym_ThenGetById_Returns201ThenReturnsGym() {
        String adminToken = registerAndGetToken("admin_create", UserRole.ADMIN);
        GymRequest request = new GymRequest("Integration FitZone " + SUFFIX, "Calle Test 1", "Madrid",
                "+34 911 000 099", "08:00-22:00");

        ResponseEntity<GymResponse> createResp = rest.exchange(
                url(GYMS_BASE), HttpMethod.POST,
                gymEntity(adminToken, request), GymResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getHeaders().getLocation()).isNotNull();

        GymResponse created = createResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Integration FitZone " + SUFFIX);
        assertThat(created.active()).isTrue();

        String locationPath = createResp.getHeaders().getLocation().getPath();
        assertThat(locationPath).isEqualTo(GYMS_BASE + "/" + created.id());

        // GET the created gym
        ResponseEntity<GymResponse> getResp = rest.exchange(
                url(GYMS_BASE + "/" + created.id()), HttpMethod.GET,
                voidEntity(adminToken), GymResponse.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        GymResponse fetched = getResp.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo("Integration FitZone " + SUFFIX);
        assertThat(fetched.city()).isEqualTo("Madrid");
    }

    @Test
    @DisplayName("ADMIN updates gym → 200 with updated fields")
    void adminUpdatesGym_Returns200WithUpdatedFields() {
        String adminToken = registerAndGetToken("admin_update", UserRole.ADMIN);
        GymRequest createRequest = new GymRequest("UpdateMe " + SUFFIX, "Old Street 1", "Seville",
                null, null);

        ResponseEntity<GymResponse> createResp = rest.exchange(
                url(GYMS_BASE), HttpMethod.POST,
                gymEntity(adminToken, createRequest), GymResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long gymId = createResp.getBody().id();

        GymRequest updateRequest = new GymRequest("UpdateMe Updated " + SUFFIX, "New Street 99", "Seville",
                "+34 954 000 099", "09:00-21:00");

        ResponseEntity<GymResponse> updateResp = rest.exchange(
                url(GYMS_BASE + "/" + gymId), HttpMethod.PUT,
                gymEntity(adminToken, updateRequest), GymResponse.class);

        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        GymResponse updated = updateResp.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.name()).isEqualTo("UpdateMe Updated " + SUFFIX);
        assertThat(updated.address()).isEqualTo("New Street 99");
        assertThat(updated.phone()).isEqualTo("+34 954 000 099");
        assertThat(updated.openingHours()).isEqualTo("09:00-21:00");
    }

    @Test
    @DisplayName("ADMIN soft-deletes gym → 204; GET returns gym with active=false")
    void adminSoftDeletesGym_Returns204_ThenGetReturnsGymWithActiveFalse() {
        String adminToken = registerAndGetToken("admin_delete", UserRole.ADMIN);
        GymRequest createRequest = new GymRequest("SoftDelete Me " + SUFFIX, "Temp Street 1", "Valencia",
                null, null);

        ResponseEntity<GymResponse> createResp = rest.exchange(
                url(GYMS_BASE), HttpMethod.POST,
                gymEntity(adminToken, createRequest), GymResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long gymId = createResp.getBody().id();

        // Soft delete
        ResponseEntity<Void> deleteResp = rest.exchange(
                url(GYMS_BASE + "/" + gymId), HttpMethod.DELETE,
                voidEntity(adminToken), Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // GET should still return the gym (soft delete, not removed)
        ResponseEntity<GymResponse> getResp = rest.exchange(
                url(GYMS_BASE + "/" + gymId), HttpMethod.GET,
                voidEntity(adminToken), GymResponse.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        GymResponse fetched = getResp.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(gymId);
        assertThat(fetched.active()).isFalse();
    }

    @Test
    @DisplayName("CUSTOMER trying to create gym → 403")
    void customerCreatesGym_Returns403() throws Exception {
        // Register a customer via explicit JSON to avoid any Jackson 2/3 serialization mismatch
        // with the UserRole enum across the test RestTemplate and the server.
        String email = "customer_create_" + SUFFIX + "@gym-it.test";
        String registerBody = """
                {"name":"Customer User","email":"%s","password":"supersecret123!"}
                """.formatted(email);

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> regResp = rest.exchange(
                url(AUTH_BASE + "/register"), HttpMethod.POST,
                new HttpEntity<>(registerBody, jsonHeaders), String.class);
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Extract the access token from the raw JSON
        String regBody = regResp.getBody();
        assertThat(regBody).contains("accessToken");
        String customerToken = objectMapper.readTree(regBody)
                .path("tokens").path("accessToken").asText();
        assertThat(customerToken).isNotBlank();

        // POST to create gym with CUSTOMER token — expect 403
        String gymBody = """
                {"name":"Customer Gym %s","address":"Street 1","city":"Madrid"}
                """.formatted(SUFFIX);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(customerToken);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = rest.exchange(
                url(GYMS_BASE), HttpMethod.POST,
                new HttpEntity<>(gymBody, authHeaders), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("CUSTOMER can list gyms → 200")
    void customerListsGyms_Returns200() {
        String customerToken = registerAndGetToken("customer_list", UserRole.CUSTOMER);

        ResponseEntity<String> resp = rest.exchange(
                url(GYMS_BASE), HttpMethod.GET,
                voidEntity(customerToken), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET gym by id that does not exist → 404")
    void getGymById_NotFound_Returns404() {
        String adminToken = registerAndGetToken("admin_notfound", UserRole.ADMIN);

        ResponseEntity<String> resp = rest.exchange(
                url(GYMS_BASE + "/999999"), HttpMethod.GET,
                voidEntity(adminToken), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ADMIN creates gym with duplicate name → 409")
    void adminCreatesGymWithDuplicateName_Returns409() {
        String adminToken = registerAndGetToken("admin_dup", UserRole.ADMIN);
        String uniqueName = "DupGym " + SUFFIX;
        GymRequest request = new GymRequest(uniqueName, "Street A", "City A", null, null);

        ResponseEntity<GymResponse> first = rest.exchange(
                url(GYMS_BASE), HttpMethod.POST,
                gymEntity(adminToken, request), GymResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = rest.exchange(
                url(GYMS_BASE), HttpMethod.POST,
                gymEntity(adminToken, request), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
