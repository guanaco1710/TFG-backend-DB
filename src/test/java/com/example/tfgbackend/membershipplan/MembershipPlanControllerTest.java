package com.example.tfgbackend.membershipplan;

// TODO: implement MembershipPlanController before these tests can go green.

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.exception.MembershipPlanInUseException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanRequest;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link MembershipPlanController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 *
 * <p>The controller does not yet exist; this test suite is intentionally written ahead of the
 * implementation (TDD). Compilation will fail until the production classes are created.
 */
@WebMvcTest(MembershipPlanController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class MembershipPlanControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean MembershipPlanService membershipPlanService;
    @MockitoBean JwtService jwtService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE = "/api/v1/membership-plans";

    // ---------------------------------------------------------------------------
    // Authentication helpers
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
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private MembershipPlanResponse planResponse(Long id) {
        return new MembershipPlanResponse(id, "Gold", "Gold membership",
                new BigDecimal("49.99"), 20, true, true);
    }

    private MembershipPlanRequest validRequest() {
        return new MembershipPlanRequest("Gold", "Gold membership",
                new BigDecimal("49.99"), 20, true, 1);
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/membership-plans
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/membership-plans — getAllPlans")
    class GetAllPlans {

        @Test
        @DisplayName("admin retrieves all plans — returns 200 with page body")
        void getAllPlans_AdminRequest_Returns200WithPage() throws Exception {
            Page<MembershipPlanResponse> page = new PageImpl<>(
                    List.of(planResponse(1L)), PageRequest.of(0, 10), 1);
            when(membershipPlanService.getAllPlans(any(), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Gold"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("customer retrieves active plans — returns 200 (public endpoint, active filter applied)")
        void getAllPlans_CustomerRequest_Returns200WithActivePlans() throws Exception {
            Page<MembershipPlanResponse> page = new PageImpl<>(
                    List.of(planResponse(1L)), PageRequest.of(0, 10), 1);
            when(membershipPlanService.getAllPlans(eq(true), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].active").value(true));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getAllPlans_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("active=true query parameter is forwarded to the service")
        void getAllPlans_ActiveFilterParam_IsForwardedToService() throws Exception {
            Page<MembershipPlanResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(membershipPlanService.getAllPlans(eq(true), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .param("active", "true")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/membership-plans/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/membership-plans/{id} — getPlanById")
    class GetPlanById {

        @Test
        @DisplayName("admin retrieves existing plan — returns 200 with plan body")
        void getPlanById_PlanExists_Returns200() throws Exception {
            when(membershipPlanService.getPlanById(1L)).thenReturn(planResponse(1L));

            mvc.perform(get(BASE + "/1")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Gold"))
                    .andExpect(jsonPath("$.priceMonthly").value(49.99));
        }

        @Test
        @DisplayName("customer retrieves existing plan — returns 200")
        void getPlanById_CustomerRequest_Returns200() throws Exception {
            when(membershipPlanService.getPlanById(1L)).thenReturn(planResponse(1L));

            mvc.perform(get(BASE + "/1")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("plan not found — returns 404 with error body")
        void getPlanById_NotFound_Returns404() throws Exception {
            when(membershipPlanService.getPlanById(999L))
                    .thenThrow(new MembershipPlanNotFoundException(999L));

            mvc.perform(get(BASE + "/999")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("MembershipPlanNotFound"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getPlanById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/membership-plans
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/membership-plans — createPlan")
    class CreatePlan {

        @Test
        @DisplayName("admin creates plan — returns 201 with Location header and plan body")
        void createPlan_AdminValidRequest_Returns201WithLocationAndBody() throws Exception {
            when(membershipPlanService.createPlan(any())).thenReturn(planResponse(1L));

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/1"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Gold"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("missing name field fails validation — returns 400")
        void createPlan_MissingName_Returns400() throws Exception {
            String body = """
                    {"name": "", "priceMonthly": 49.99, "allowsWaitlist": true, "durationMonths": 1}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("missing priceMonthly fails validation — returns 400")
        void createPlan_MissingPrice_Returns400() throws Exception {
            String body = """
                    {"name": "Gold", "allowsWaitlist": true, "durationMonths": 1}
                    """;

            mvc.perform(post(BASE)
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("customer is forbidden from creating plans — returns 403")
        void createPlan_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void createPlan_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // PATCH /api/v1/membership-plans/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/membership-plans/{id} — updatePlan")
    class UpdatePlan {

        @Test
        @DisplayName("admin updates existing plan — returns 200 with updated body")
        void updatePlan_AdminValidRequest_Returns200() throws Exception {
            MembershipPlanResponse updated = new MembershipPlanResponse(
                    1L, "Gold Plus", "Updated", new BigDecimal("59.99"), 25, true, true);
            when(membershipPlanService.updatePlan(eq(1L), any())).thenReturn(updated);

            mvc.perform(patch(BASE + "/1")
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Gold Plus"));
        }

        @Test
        @DisplayName("plan not found — returns 404")
        void updatePlan_NotFound_Returns404() throws Exception {
            when(membershipPlanService.updatePlan(eq(999L), any()))
                    .thenThrow(new MembershipPlanNotFoundException(999L));

            mvc.perform(patch(BASE + "/999")
                            .with(authentication(adminAuth(99L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("MembershipPlanNotFound"));
        }

        @Test
        @DisplayName("customer is forbidden from updating plans — returns 403")
        void updatePlan_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(patch(BASE + "/1")
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void updatePlan_Unauthenticated_Returns401() throws Exception {
            mvc.perform(patch(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/membership-plans/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/membership-plans/{id} — deactivatePlan")
    class DeactivatePlan {

        @Test
        @DisplayName("admin deactivates existing plan — returns 204")
        void deactivatePlan_AdminRequest_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/1")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("plan not found — returns 404")
        void deactivatePlan_NotFound_Returns404() throws Exception {
            doThrow(new MembershipPlanNotFoundException(999L))
                    .when(membershipPlanService).deactivatePlan(999L);

            mvc.perform(delete(BASE + "/999")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("MembershipPlanNotFound"));
        }

        @Test
        @DisplayName("plan in use by active subscriptions — returns 409")
        void deactivatePlan_PlanInUse_Returns409() throws Exception {
            doThrow(new MembershipPlanInUseException(1L))
                    .when(membershipPlanService).deactivatePlan(1L);

            mvc.perform(delete(BASE + "/1")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("MembershipPlanInUse"));
        }

        @Test
        @DisplayName("customer is forbidden from deactivating plans — returns 403")
        void deactivatePlan_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(delete(BASE + "/1")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void deactivatePlan_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
