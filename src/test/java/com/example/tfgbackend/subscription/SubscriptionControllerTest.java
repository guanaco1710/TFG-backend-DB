package com.example.tfgbackend.subscription;

// TODO: implement SubscriptionController before these tests can go green.

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.exception.MembershipPlanInactiveException;
import com.example.tfgbackend.common.exception.NoActiveSubscriptionException;
import com.example.tfgbackend.common.exception.SubscriptionAlreadyActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.subscription.dto.CreateSubscriptionRequest;
import com.example.tfgbackend.subscription.dto.MembershipPlanSummary;
import com.example.tfgbackend.subscription.dto.SubscriptionResponse;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link SubscriptionController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed.
 *
 * <p>The controller does not yet exist; this test suite is intentionally written ahead of the
 * implementation (TDD). Compilation will fail until the production classes are created.
 */
@WebMvcTest(SubscriptionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class SubscriptionControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean SubscriptionService subscriptionService;
    @MockitoBean JwtService jwtService;

    @Autowired ObjectMapper mapper;

    private static final String BASE = "/api/v1/subscriptions";

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

    private SubscriptionResponse subscriptionResponse(Long id, SubscriptionStatus status) {
        MembershipPlanSummary planSummary = new MembershipPlanSummary(10L, "Gold", new BigDecimal("49.99"));
        return new SubscriptionResponse(id, planSummary, status,
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), 3, 17);
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/subscriptions
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/subscriptions — getAllSubscriptions (admin)")
    class GetAllSubscriptions {

        @Test
        @DisplayName("admin retrieves all subscriptions — returns 200 with page")
        void getAllSubscriptions_AdminRequest_Returns200WithPage() throws Exception {
            Page<SubscriptionResponse> page = new PageImpl<>(
                    List.of(subscriptionResponse(100L, SubscriptionStatus.ACTIVE)),
                    PageRequest.of(0, 10), 1);
            when(subscriptionService.getAllSubscriptions(any(), any(), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(100))
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("customer is forbidden from the admin list endpoint — returns 403")
        void getAllSubscriptions_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(get(BASE)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getAllSubscriptions_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("admin filters by userId query param — service receives userId")
        void getAllSubscriptions_UserIdFilterParam_IsForwardedToService() throws Exception {
            Page<SubscriptionResponse> page = new PageImpl<>(
                    List.of(subscriptionResponse(100L, SubscriptionStatus.ACTIVE)),
                    PageRequest.of(0, 10), 1);
            when(subscriptionService.getAllSubscriptions(eq(1L), any(), any())).thenReturn(page);

            mvc.perform(get(BASE)
                            .param("userId", "1")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("admin filters by status query param — service receives status")
        void getAllSubscriptions_StatusFilterParam_IsForwardedToService() throws Exception {
            Page<SubscriptionResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(subscriptionService.getAllSubscriptions(any(), eq(SubscriptionStatus.CANCELLED), any()))
                    .thenReturn(page);

            mvc.perform(get(BASE)
                            .param("status", "CANCELLED")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/subscriptions
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/subscriptions — subscribe")
    class Subscribe {

        @Test
        @DisplayName("customer subscribes to a plan — returns 201 with Location and body")
        void subscribe_CustomerValidRequest_Returns201WithLocationAndBody() throws Exception {
            SubscriptionResponse response = subscriptionResponse(200L, SubscriptionStatus.ACTIVE);
            when(subscriptionService.subscribe(eq(1L), eq(10L))).thenReturn(response);

            CreateSubscriptionRequest body = new CreateSubscriptionRequest(10L);

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/200"))
                    .andExpect(jsonPath("$.id").value(200))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.plan.id").value(10));
        }

        @Test
        @DisplayName("missing membershipPlanId fails validation — returns 400")
        void subscribe_MissingMembershipPlanId_Returns400() throws Exception {
            mvc.perform(post(BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"membershipPlanId\": null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("user already has active subscription — returns 409")
        void subscribe_AlreadyActiveSubscription_Returns409() throws Exception {
            when(subscriptionService.subscribe(any(), any()))
                    .thenThrow(new SubscriptionAlreadyActiveException(1L));

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new CreateSubscriptionRequest(10L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SubscriptionAlreadyActive"));
        }

        @Test
        @DisplayName("plan is inactive — returns 409")
        void subscribe_PlanInactive_Returns409() throws Exception {
            when(subscriptionService.subscribe(any(), any()))
                    .thenThrow(new MembershipPlanInactiveException(11L));

            mvc.perform(post(BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new CreateSubscriptionRequest(11L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("MembershipPlanInactive"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void subscribe_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new CreateSubscriptionRequest(10L))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/subscriptions/me
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/subscriptions/me — getMyActiveSubscription")
    class GetMyActiveSubscription {

        @Test
        @DisplayName("customer retrieves own active subscription — returns 200 with body")
        void getMyActiveSubscription_HasActiveSubscription_Returns200() throws Exception {
            SubscriptionResponse response = subscriptionResponse(100L, SubscriptionStatus.ACTIVE);
            when(subscriptionService.getMyActiveSubscription(1L)).thenReturn(response);

            mvc.perform(get(BASE + "/me")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.plan.name").value("Gold"))
                    .andExpect(jsonPath("$.classesUsedThisMonth").value(3))
                    .andExpect(jsonPath("$.classesRemainingThisMonth").value(17));
        }

        @Test
        @DisplayName("no active subscription — returns 404")
        void getMyActiveSubscription_NoActiveSubscription_Returns404() throws Exception {
            when(subscriptionService.getMyActiveSubscription(1L))
                    .thenThrow(new NoActiveSubscriptionException(1L));

            mvc.perform(get(BASE + "/me")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NoActiveSubscription"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void getMyActiveSubscription_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/subscriptions/{id}/cancel
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/subscriptions/{id}/cancel — cancelSubscription")
    class CancelSubscription {

        @Test
        @DisplayName("customer cancels own active subscription — returns 204")
        void cancelSubscription_OwnerCancels_Returns204() throws Exception {
            mvc.perform(post(BASE + "/100/cancel")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("admin cancels any subscription — returns 204")
        void cancelSubscription_AdminCancels_Returns204() throws Exception {
            mvc.perform(post(BASE + "/100/cancel")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("subscription not active (already cancelled) — returns 409")
        void cancelSubscription_AlreadyCancelled_Returns409() throws Exception {
            doThrow(new SubscriptionNotActiveException(101L))
                    .when(subscriptionService).cancelSubscription(eq(101L), any(), any());

            mvc.perform(post(BASE + "/101/cancel")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SubscriptionNotActive"));
        }

        @Test
        @DisplayName("subscription not found — returns 404")
        void cancelSubscription_SubscriptionNotFound_Returns404() throws Exception {
            doThrow(new SubscriptionNotFoundException(999L))
                    .when(subscriptionService).cancelSubscription(eq(999L), any(), any());

            mvc.perform(post(BASE + "/999/cancel")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SubscriptionNotFound"));
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void cancelSubscription_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE + "/100/cancel"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/subscriptions/{id}/renew
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/subscriptions/{id}/renew — renewSubscription (admin only)")
    class RenewSubscription {

        @Test
        @DisplayName("admin renews subscription — returns 200 with updated body")
        void renewSubscription_AdminRequest_Returns200WithUpdatedBody() throws Exception {
            SubscriptionResponse response = subscriptionResponse(100L, SubscriptionStatus.ACTIVE);
            when(subscriptionService.renewSubscription(100L)).thenReturn(response);

            mvc.perform(post(BASE + "/100/renew")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("subscription not found — returns 404")
        void renewSubscription_SubscriptionNotFound_Returns404() throws Exception {
            when(subscriptionService.renewSubscription(999L))
                    .thenThrow(new SubscriptionNotFoundException(999L));

            mvc.perform(post(BASE + "/999/renew")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SubscriptionNotFound"));
        }

        @Test
        @DisplayName("customer is forbidden from renewing subscriptions — returns 403")
        void renewSubscription_CustomerForbidden_Returns403() throws Exception {
            mvc.perform(post(BASE + "/100/renew")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request — returns 401")
        void renewSubscription_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE + "/100/renew"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
