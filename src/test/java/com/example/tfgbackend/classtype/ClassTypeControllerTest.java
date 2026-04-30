package com.example.tfgbackend.classtype;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.classtype.dto.ClassTypeResponse;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.ClassTypeInUseException;
import com.example.tfgbackend.common.exception.ClassTypeNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.UserRole;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * Slice test for {@link ClassTypeController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 */
@WebMvcTest(ClassTypeController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ClassTypeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ClassTypeService classTypeService;

    @MockitoBean
    JwtService jwtService;

    private static final String BASE = "/api/v1/class-types";

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

    // ---------------------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private ClassTypeResponse classTypeResponse(Long id) {
        return new ClassTypeResponse(id, "Spinning 45min", "High-intensity cycling", "INTERMEDIATE");
    }

    private PageResponse<ClassTypeResponse> singlePageResponse(ClassTypeResponse classType) {
        return new PageResponse<>(List.of(classType), 0, 10, 1L, 1, false);
    }

    private String validCreateJson() {
        return """
                {
                  "name": "Spinning 45min",
                  "description": "High-intensity cycling",
                  "level": "INTERMEDIATE"
                }
                """;
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-types — list
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-types — list")
    class ListClassTypes {

        @Test
        @DisplayName("authenticated user retrieves list — returns 200 with paginated body")
        void list_AuthenticatedUser_Returns200WithPage() throws Exception {
            when(classTypeService.list(any(), any(), any()))
                    .thenReturn(singlePageResponse(classTypeResponse(1L)));

            mvc.perform(get(BASE).with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Spinning 45min"))
                    .andExpect(jsonPath("$.content[0].level").value("INTERMEDIATE"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void list_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-types/{id} — getById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-types/{id} — getById")
    class GetById {

        @Test
        @DisplayName("authenticated user retrieves existing class type — returns 200 with body")
        void getById_ClassTypeExists_Returns200() throws Exception {
            when(classTypeService.getById(1L)).thenReturn(classTypeResponse(1L));

            mvc.perform(get(BASE + "/1").with(authentication(customerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Spinning 45min"))
                    .andExpect(jsonPath("$.description").value("High-intensity cycling"))
                    .andExpect(jsonPath("$.level").value("INTERMEDIATE"));
        }

        @Test
        @DisplayName("class type not found — returns 404 with ClassTypeNotFound error")
        void getById_NotFound_Returns404() throws Exception {
            when(classTypeService.getById(999L)).thenThrow(new ClassTypeNotFoundException(999L));

            mvc.perform(get(BASE + "/999").with(authentication(customerAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ClassTypeNotFound"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/class-types — create
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/class-types — create")
    class Create {

        @Test
        @DisplayName("admin creates class type — returns 201 with Location header and body")
        void create_AdminValidRequest_Returns201WithLocationAndBody() throws Exception {
            when(classTypeService.create(any())).thenReturn(classTypeResponse(10L));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/10"))
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Spinning 45min"))
                    .andExpect(jsonPath("$.level").value("INTERMEDIATE"));
        }

        @Test
        @DisplayName("non-ADMIN (CUSTOMER) is forbidden — returns 403")
        void create_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void create_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("missing name field — returns 400 with ValidationFailed error")
        void create_MissingName_Returns400() throws Exception {
            String body = """
                    {
                      "description": "Some description",
                      "level": "BASIC"
                    }
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("missing level field — returns 400 with ValidationFailed error")
        void create_MissingLevel_Returns400() throws Exception {
            String body = """
                    {
                      "name": "Spinning",
                      "description": "Some description"
                    }
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("invalid level value — returns 400 with ValidationFailed error")
        void create_InvalidLevel_Returns400() throws Exception {
            String body = """
                    {
                      "name": "Spinning",
                      "description": "Some description",
                      "level": "INVALID"
                    }
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("duplicate name — returns 409 with ClassTypeNameAlreadyExists error")
        void create_DuplicateName_Returns409() throws Exception {
            when(classTypeService.create(any()))
                    .thenThrow(new ClassTypeNameAlreadyExistsException("Spinning 45min"));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ClassTypeNameAlreadyExists"));
        }
    }

    // ---------------------------------------------------------------------------
    // PUT /api/v1/class-types/{id} — update
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/v1/class-types/{id} — update")
    class Update {

        @Test
        @DisplayName("admin updates class type — returns 200 with updated body")
        void update_AdminValidRequest_Returns200() throws Exception {
            ClassTypeResponse updated = new ClassTypeResponse(1L, "Spinning 60min", "Extended session", "ADVANCED");
            when(classTypeService.update(eq(1L), any())).thenReturn(updated);

            mvc.perform(put(BASE + "/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Spinning 60min"));
        }

        @Test
        @DisplayName("non-ADMIN (CUSTOMER) is forbidden — returns 403")
        void update_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(put(BASE + "/1")
                            .with(authentication(customerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("class type not found — returns 404 with ClassTypeNotFound error")
        void update_NotFound_Returns404() throws Exception {
            when(classTypeService.update(eq(999L), any()))
                    .thenThrow(new ClassTypeNotFoundException(999L));

            mvc.perform(put(BASE + "/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ClassTypeNotFound"));
        }

        @Test
        @DisplayName("duplicate name — returns 409 with ClassTypeNameAlreadyExists error")
        void update_DuplicateName_Returns409() throws Exception {
            when(classTypeService.update(eq(1L), any()))
                    .thenThrow(new ClassTypeNameAlreadyExistsException("Yoga Flow"));

            mvc.perform(put(BASE + "/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreateJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ClassTypeNameAlreadyExists"));
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/class-types/{id} — delete
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/class-types/{id} — delete")
    class DeleteClassType {

        @Test
        @DisplayName("admin deletes class type — returns 204 no content")
        void delete_AdminRequest_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/1").with(authentication(adminAuth())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("non-ADMIN (CUSTOMER) is forbidden — returns 403")
        void delete_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(delete(BASE + "/1").with(authentication(customerAuth())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("class type not found — returns 404 with ClassTypeNotFound error")
        void delete_NotFound_Returns404() throws Exception {
            doThrow(new ClassTypeNotFoundException(999L)).when(classTypeService).delete(999L);

            mvc.perform(delete(BASE + "/999").with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ClassTypeNotFound"));
        }

        @Test
        @DisplayName("class type in use by sessions — returns 409 with ClassTypeInUse error")
        void delete_InUse_Returns409() throws Exception {
            doThrow(new ClassTypeInUseException(1L)).when(classTypeService).delete(1L);

            mvc.perform(delete(BASE + "/1").with(authentication(adminAuth())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ClassTypeInUse"));
        }
    }
}
