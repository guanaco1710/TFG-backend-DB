package com.example.tfgbackend.user;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.SpecialtyNotAllowedException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.dto.AdminUpdateUserRequest;
import com.example.tfgbackend.user.dto.UpdateUserRequest;
import com.example.tfgbackend.user.dto.UserResponse;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link UserController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link SecurityMockMvcRequestPostProcessors#authentication} so the JWT filter is bypassed.
 */
@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserService userService;
    @MockitoBean JwtService jwtService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE = "/api/v1/users";

    private static final UserResponse ALICE_RESPONSE = new UserResponse(
            1L, "Alice", "alice@test.com", "600000001", UserRole.CUSTOMER, true, Instant.now(), null);

    private static final UserResponse ADMIN_RESPONSE = new UserResponse(
            99L, "Admin", "admin@test.com", null, UserRole.ADMIN, true, Instant.now(), null);

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "alice@test.com", UserRole.CUSTOMER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "admin@test.com", UserRole.ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/users/me
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/me — me")
    class GetMe {

        @Test
        @DisplayName("authenticated user returns 200 with profile")
        void me_Authenticated_Returns200WithProfile() throws Exception {
            when(userService.getById(1L)).thenReturn(ALICE_RESPONSE);

            mvc.perform(get(BASE + "/me")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("alice@test.com"))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void me_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // PATCH /api/v1/users/me
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/users/me — updateMe")
    class UpdateMe {

        @Test
        @DisplayName("authenticated user updates profile returns 200")
        void updateMe_ValidRequest_Returns200() throws Exception {
            UpdateUserRequest req = new UpdateUserRequest("Alice Updated", "600000002", null);
            UserResponse updated = new UserResponse(
                    1L, "Alice Updated", "alice@test.com", "600000002", UserRole.CUSTOMER, true, Instant.now(), null);

            when(userService.updateMe(eq(1L), any())).thenReturn(updated);

            mvc.perform(patch(BASE + "/me")
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alice Updated"))
                    .andExpect(jsonPath("$.phone").value("600000002"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void updateMe_Unauthenticated_Returns401() throws Exception {
            UpdateUserRequest req = new UpdateUserRequest("Alice", null, null);

            mvc.perform(patch(BASE + "/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("non-instructor setting specialty returns 400 SpecialtyNotAllowed")
        void updateMe_SpecialtyNotAllowed_Returns400() throws Exception {
            UpdateUserRequest req = new UpdateUserRequest(null, null, "Spinning");
            when(userService.updateMe(eq(1L), any())).thenThrow(new SpecialtyNotAllowedException());

            mvc.perform(patch(BASE + "/me")
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SpecialtyNotAllowed"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/users
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users — listUsers (ADMIN only)")
    class ListUsers {

        @Test
        @DisplayName("admin without role filter returns 200 with paginated users")
        void listUsers_Admin_Returns200() throws Exception {
            PageResponse<UserResponse> page = new PageResponse<>(
                    List.of(ALICE_RESPONSE), 0, 10, 1L, 1, false);

            when(userService.listUsers(eq(null), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("admin with role filter forwards filter to service")
        void listUsers_AdminWithRoleFilter_ForwardsFilterToService() throws Exception {
            PageResponse<UserResponse> page = new PageResponse<>(
                    List.of(ALICE_RESPONSE), 0, 10, 1L, 1, false);

            when(userService.listUsers(eq(UserRole.CUSTOMER), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .param("role", "CUSTOMER")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].role").value("CUSTOMER"));
        }

        @Test
        @DisplayName("customer is forbidden — returns 403")
        void listUsers_Customer_Returns403() throws Exception {
            mvc.perform(get(BASE)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void listUsers_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/users/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{id} — getUserById (ADMIN only)")
    class GetUserById {

        @Test
        @DisplayName("admin retrieves existing user returns 200")
        void getUserById_Admin_Returns200() throws Exception {
            when(userService.getById(1L)).thenReturn(ALICE_RESPONSE);

            mvc.perform(get(BASE + "/1")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("alice@test.com"));
        }

        @Test
        @DisplayName("user not found returns 404")
        void getUserById_NotFound_Returns404() throws Exception {
            when(userService.getById(999L)).thenThrow(new UserNotFoundException(999L));

            mvc.perform(get(BASE + "/999")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("UserNotFound"));
        }

        @Test
        @DisplayName("customer is forbidden — returns 403")
        void getUserById_Customer_Returns403() throws Exception {
            mvc.perform(get(BASE + "/1")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getUserById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // PATCH /api/v1/users/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/users/{id} — adminUpdateUser (ADMIN only)")
    class AdminUpdateUser {

        @Test
        @DisplayName("admin updates user returns 200 with updated body")
        void adminUpdateUser_Admin_Returns200() throws Exception {
            AdminUpdateUserRequest req = new AdminUpdateUserRequest("New Name", null, UserRole.INSTRUCTOR, null, null);
            UserResponse updated = new UserResponse(
                    1L, "New Name", "alice@test.com", "600000001", UserRole.INSTRUCTOR, true, Instant.now(), null);

            when(userService.adminUpdateUser(eq(1L), any())).thenReturn(updated);

            mvc.perform(patch(BASE + "/1")
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
        }

        @Test
        @DisplayName("user not found returns 404")
        void adminUpdateUser_NotFound_Returns404() throws Exception {
            AdminUpdateUserRequest req = new AdminUpdateUserRequest("Name", null, null, null, null);
            when(userService.adminUpdateUser(eq(999L), any())).thenThrow(new UserNotFoundException(999L));

            mvc.perform(patch(BASE + "/999")
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("UserNotFound"));
        }

        @Test
        @DisplayName("customer is forbidden — returns 403")
        void adminUpdateUser_Customer_Returns403() throws Exception {
            AdminUpdateUserRequest req = new AdminUpdateUserRequest("Name", null, null, null, null);

            mvc.perform(patch(BASE + "/1")
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void adminUpdateUser_Unauthenticated_Returns401() throws Exception {
            AdminUpdateUserRequest req = new AdminUpdateUserRequest("Name", null, null, null, null);

            mvc.perform(patch(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("specialty on non-instructor returns 400 SpecialtyNotAllowed")
        void adminUpdateUser_SpecialtyNotAllowed_Returns400() throws Exception {
            AdminUpdateUserRequest req = new AdminUpdateUserRequest(null, null, null, null, "Spinning");
            when(userService.adminUpdateUser(eq(1L), any())).thenThrow(new SpecialtyNotAllowedException());

            mvc.perform(patch(BASE + "/1")
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SpecialtyNotAllowed"));
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/users/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/users/{id} — deactivateUser (ADMIN only)")
    class DeactivateUser {

        @Test
        @DisplayName("admin soft-deletes user returns 204")
        void deactivateUser_Admin_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/1")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("user not found returns 404")
        void deactivateUser_NotFound_Returns404() throws Exception {
            doThrow(new UserNotFoundException(999L))
                    .when(userService).deactivateUser(999L);

            mvc.perform(delete(BASE + "/999")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("UserNotFound"));
        }

        @Test
        @DisplayName("customer is forbidden — returns 403")
        void deactivateUser_Customer_Returns403() throws Exception {
            mvc.perform(delete(BASE + "/1")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void deactivateUser_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
