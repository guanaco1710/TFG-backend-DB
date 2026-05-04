package com.example.tfgbackend.gym;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.GymNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.dto.GymRequest;
import com.example.tfgbackend.gym.dto.GymResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link GymController}.
 *
 * Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 */
@WebMvcTest(GymController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class GymControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GymService gymService;

    @MockitoBean
    JwtService jwtService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE = "/api/v1/gyms";

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken adminAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "admin@test.com", UserRole.ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken customerAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(2L, "customer@test.com", UserRole.CUSTOMER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken instructorAuth() {
        AuthenticatedUser principal = new AuthenticatedUser(3L, "instructor@test.com", UserRole.INSTRUCTOR);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")));
    }

    // ---------------------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private GymResponse gymResponse(Long id) {
        return new GymResponse(id, "FitZone Madrid", "Calle Mayor 1", "Madrid",
                "+34 911 000 001", "07:00-22:00", true, Instant.now(), Instant.now());
    }

    private GymRequest validRequest() {
        return new GymRequest("FitZone Madrid", "Calle Mayor 1", "Madrid",
                "+34 911 000 001", "07:00-22:00");
    }

    private PageResponse<GymResponse> singlePageResponse(GymResponse gym) {
        return new PageResponse<>(List.of(gym), 0, 10, 1, 1, false);
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/gyms — listGyms
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/gyms — listGyms")
    class ListGyms {

        @Test
        @DisplayName("authenticated user retrieves gym list — returns 200 with page body")
        void listGyms_AuthenticatedUser_Returns200WithPage() throws Exception {
            when(gymService.listGyms(any(), any(), any(), any(), any()))
                    .thenReturn(singlePageResponse(gymResponse(1L)));

            mvc.perform(get(BASE).with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("FitZone Madrid"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("admin retrieves gym list — returns 200")
        void listGyms_AdminUser_Returns200() throws Exception {
            when(gymService.listGyms(any(), any(), any(), any(), any()))
                    .thenReturn(singlePageResponse(gymResponse(1L)));

            mvc.perform(get(BASE).with(authentication(adminAuth())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void listGyms_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("query parameters are forwarded — returns 200")
        void listGyms_WithQueryParams_Returns200() throws Exception {
            when(gymService.listGyms(eq("Madrid"), eq(true), eq("FitZone"), eq("Central"), any()))
                    .thenReturn(singlePageResponse(gymResponse(1L)));

            mvc.perform(get(BASE)
                            .param("city", "Madrid")
                            .param("active", "true")
                            .param("name", "FitZone")
                            .param("q", "Central")
                            .with(authentication(customerAuth())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("name parameter is forwarded to service — returns 200")
        void listGyms_WithNameParam_Returns200() throws Exception {
            when(gymService.listGyms(isNull(), isNull(), eq("GymBook"), isNull(), any()))
                    .thenReturn(singlePageResponse(gymResponse(1L)));

            mvc.perform(get(BASE)
                            .param("name", "GymBook")
                            .with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("FitZone Madrid"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/gyms/{id} — getById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/gyms/{id} — getById")
    class GetById {

        @Test
        @DisplayName("authenticated user retrieves existing gym — returns 200 with gym body")
        void getById_GymExists_Returns200() throws Exception {
            when(gymService.getById(1L)).thenReturn(gymResponse(1L));

            mvc.perform(get(BASE + "/1").with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("FitZone Madrid"))
                    .andExpect(jsonPath("$.city").value("Madrid"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("gym not found — returns 404 with GymNotFound error body")
        void getById_GymNotFound_Returns404() throws Exception {
            when(gymService.getById(999L)).thenThrow(new GymNotFoundException(999L));

            mvc.perform(get(BASE + "/999").with(authentication(customerAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("GymNotFound"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/gyms — create
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/gyms — create")
    class Create {

        @Test
        @DisplayName("admin creates gym — returns 201 with Location header and gym body")
        void create_AdminValidRequest_Returns201WithLocationAndBody() throws Exception {
            when(gymService.create(any())).thenReturn(gymResponse(1L));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/1"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("FitZone Madrid"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("customer is forbidden from creating gyms — returns 403")
        void create_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("instructor is forbidden from creating gyms — returns 403")
        void create_InstructorForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(instructorAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("missing name fails validation — returns 400")
        void create_MissingName_Returns400() throws Exception {
            String body = """
                    {"name": "", "address": "Calle Mayor 1", "city": "Madrid"}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("missing address fails validation — returns 400")
        void create_MissingAddress_Returns400() throws Exception {
            String body = """
                    {"name": "FitZone", "city": "Madrid"}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("missing city fails validation — returns 400")
        void create_MissingCity_Returns400() throws Exception {
            String body = """
                    {"name": "FitZone", "address": "Calle Mayor 1"}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("invalid phone format fails validation — returns 400")
        void create_InvalidPhone_Returns400() throws Exception {
            String body = """
                    {"name": "FitZone", "address": "Calle Mayor", "city": "Madrid", "phone": "BAD"}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void create_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // PUT /api/v1/gyms/{id} — update
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/gyms/{id} — update")
    class Update {

        @Test
        @DisplayName("admin updates existing gym — returns 200 with updated body")
        void update_AdminValidRequest_Returns200() throws Exception {
            GymResponse updated = new GymResponse(1L, "FitZone Madrid Updated", "New Street",
                    "Madrid", "+34 911 999 999", "08:00-23:00", true, Instant.now(), Instant.now());
            when(gymService.update(eq(1L), any())).thenReturn(updated);

            mvc.perform(put(BASE + "/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("FitZone Madrid Updated"));
        }

        @Test
        @DisplayName("customer is forbidden from updating gyms — returns 403")
        void update_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(put(BASE + "/1")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("gym not found — returns 404")
        void update_GymNotFound_Returns404() throws Exception {
            when(gymService.update(eq(999L), any())).thenThrow(new GymNotFoundException(999L));

            mvc.perform(put(BASE + "/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("GymNotFound"));
        }

        @Test
        @DisplayName("duplicate gym name — returns 409 with GymNameAlreadyExists error")
        void update_DuplicateName_Returns409() throws Exception {
            when(gymService.update(eq(1L), any()))
                    .thenThrow(new GymNameAlreadyExistsException("CrossFit Barcelona"));

            mvc.perform(put(BASE + "/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("GymNameAlreadyExists"));
        }

        @Test
        @DisplayName("invalid request body — returns 400")
        void update_InvalidBody_Returns400() throws Exception {
            String body = """
                    {"name": "", "address": "Street", "city": "City"}
                    """;

            mvc.perform(put(BASE + "/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void update_Unauthenticated_Returns401() throws Exception {
            mvc.perform(put(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/gyms/{id} — delete
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/gyms/{id} — delete")
    class Delete {

        @Test
        @DisplayName("admin soft-deletes gym — returns 204")
        void delete_AdminRequest_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/1").with(authentication(adminAuth())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("customer is forbidden from deleting gyms — returns 403")
        void delete_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(delete(BASE + "/1").with(authentication(customerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("gym not found — returns 404")
        void delete_GymNotFound_Returns404() throws Exception {
            doThrow(new GymNotFoundException(999L)).when(gymService).delete(999L);

            mvc.perform(delete(BASE + "/999").with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("GymNotFound"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void delete_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
