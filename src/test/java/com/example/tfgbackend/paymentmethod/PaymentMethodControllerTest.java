package com.example.tfgbackend.paymentmethod;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.exception.CardExpiredException;
import com.example.tfgbackend.common.exception.DuplicatePaymentMethodException;
import com.example.tfgbackend.common.exception.InvalidDefaultToggleException;
import com.example.tfgbackend.common.exception.PaymentMethodNotFoundException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.paymentmethod.dto.PaymentMethodResponse;
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
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
 * Slice test for {@link PaymentMethodController}.
 *
 * <p>Spring Security is active; requests are authenticated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * so the JWT filter is bypassed. Written TDD-style before the implementation exists.
 */
@WebMvcTest(PaymentMethodController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class PaymentMethodControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentMethodService paymentMethodService;

    @MockitoBean
    JwtService jwtService;

    private static final String BASE = "/api/v1/users/{userId}/payment-methods";
    private static final int FUTURE_YEAR = YearMonth.now().getYear() + 2;

    // ---------------------------------------------------------------------------
    // Auth helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "customer@test.com", UserRole.CUSTOMER);
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

    private PaymentMethodResponse aPaymentMethodResponse(Long id) {
        return new PaymentMethodResponse(
                id,
                CardType.VISA,
                "4242",
                6,
                FUTURE_YEAR,
                "Alice Smith",
                true,
                Instant.parse("2026-01-01T10:00:00Z"));
    }

    private String validAddJson() {
        return """
                {
                  "cardType": "VISA",
                  "last4": "4242",
                  "expiryMonth": 6,
                  "expiryYear": %d,
                  "cardholderName": "Alice Smith"
                }
                """.formatted(FUTURE_YEAR);
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/users/{userId}/payment-methods
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/payment-methods — listPaymentMethods")
    class ListPaymentMethods {

        @Test
        @DisplayName("owner lists own payment methods — 200 with array")
        void listPaymentMethods_Owner_Returns200WithList() throws Exception {
            when(paymentMethodService.listPaymentMethods(1L))
                    .thenReturn(List.of(aPaymentMethodResponse(10L)));

            mvc.perform(get(BASE, 1L)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].cardType").value("VISA"))
                    .andExpect(jsonPath("$[0].last4").value("4242"))
                    .andExpect(jsonPath("$[0].isDefault").value(true));
        }

        @Test
        @DisplayName("admin lists any user's payment methods — 200")
        void listPaymentMethods_Admin_Returns200WithList() throws Exception {
            when(paymentMethodService.listPaymentMethods(1L))
                    .thenReturn(List.of(aPaymentMethodResponse(10L)));

            mvc.perform(get(BASE, 1L)
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(10));
        }

        @Test
        @DisplayName("unauthenticated — 401")
        void listPaymentMethods_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE, 1L))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("different user (IDOR) — 404 via PaymentMethodNotFoundException")
        void listPaymentMethods_DifferentUser_Returns404() throws Exception {
            when(paymentMethodService.listPaymentMethods(1L))
                    .thenThrow(new PaymentMethodNotFoundException(1L));

            mvc.perform(get(BASE, 1L)
                            .with(authentication(customerAuth(2L)))) // customer 2 accessing user 1
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PaymentMethodNotFound"));
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/users/{userId}/payment-methods
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/users/{userId}/payment-methods — addPaymentMethod")
    class AddPaymentMethod {

        @Test
        @DisplayName("owner adds valid card — 201 with Location header and body")
        void addPaymentMethod_ValidRequest_Returns201WithLocation() throws Exception {
            when(paymentMethodService.addPaymentMethod(eq(1L), any()))
                    .thenReturn(aPaymentMethodResponse(10L));

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validAddJson()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            "/api/v1/users/1/payment-methods/10"))
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.cardType").value("VISA"))
                    .andExpect(jsonPath("$.last4").value("4242"))
                    .andExpect(jsonPath("$.isDefault").value(true));
        }

        @Test
        @DisplayName("duplicate card — 409 with DuplicatePaymentMethod error")
        void addPaymentMethod_DuplicateCard_Returns409() throws Exception {
            when(paymentMethodService.addPaymentMethod(eq(1L), any()))
                    .thenThrow(new DuplicatePaymentMethodException(1L, CardType.VISA, "4242"));

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validAddJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("DuplicatePaymentMethod"));
        }

        @Test
        @DisplayName("expired card — 409 with CardExpired error")
        void addPaymentMethod_ExpiredCard_Returns409() throws Exception {
            when(paymentMethodService.addPaymentMethod(eq(1L), any()))
                    .thenThrow(new CardExpiredException(1, 2020));

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validAddJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CardExpired"));
        }

        @Test
        @DisplayName("missing cardholderName — 400 with ValidationFailed error")
        void addPaymentMethod_MissingCardholderName_Returns400() throws Exception {
            String body = """
                    {
                      "cardType": "VISA",
                      "last4": "4242",
                      "expiryMonth": 6,
                      "expiryYear": %d
                    }
                    """.formatted(FUTURE_YEAR);

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("missing cardType — 400 with ValidationFailed error")
        void addPaymentMethod_MissingCardType_Returns400() throws Exception {
            String body = """
                    {
                      "last4": "4242",
                      "expiryMonth": 6,
                      "expiryYear": %d,
                      "cardholderName": "Alice Smith"
                    }
                    """.formatted(FUTURE_YEAR);

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("expiryMonth out of range (0) — 400 with ValidationFailed error")
        void addPaymentMethod_ExpiryMonthZero_Returns400() throws Exception {
            String body = """
                    {
                      "cardType": "VISA",
                      "last4": "4242",
                      "expiryMonth": 0,
                      "expiryYear": %d,
                      "cardholderName": "Alice Smith"
                    }
                    """.formatted(FUTURE_YEAR);

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("expiryMonth out of range (13) — 400 with ValidationFailed error")
        void addPaymentMethod_ExpiryMonth13_Returns400() throws Exception {
            String body = """
                    {
                      "cardType": "VISA",
                      "last4": "4242",
                      "expiryMonth": 13,
                      "expiryYear": %d,
                      "cardholderName": "Alice Smith"
                    }
                    """.formatted(FUTURE_YEAR);

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("expiryYear below minimum (2024) — 400 with ValidationFailed error")
        void addPaymentMethod_ExpiryYearBelowMin_Returns400() throws Exception {
            String body = """
                    {
                      "cardType": "VISA",
                      "last4": "4242",
                      "expiryMonth": 6,
                      "expiryYear": 2024,
                      "cardholderName": "Alice Smith"
                    }
                    """;

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("blank last4 — 400 with ValidationFailed error")
        void addPaymentMethod_BlankLast4_Returns400() throws Exception {
            String body = """
                    {
                      "cardType": "VISA",
                      "last4": "   ",
                      "expiryMonth": 6,
                      "expiryYear": %d,
                      "cardholderName": "Alice Smith"
                    }
                    """.formatted(FUTURE_YEAR);

            mvc.perform(post(BASE, 1L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("unauthenticated — 401")
        void addPaymentMethod_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BASE, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validAddJson()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/users/{userId}/payment-methods/{pmId}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/users/{userId}/payment-methods/{pmId} — getPaymentMethod")
    class GetPaymentMethod {

        @Test
        @DisplayName("owner retrieves own card — 200 with correct body")
        void getPaymentMethod_Owner_Returns200() throws Exception {
            when(paymentMethodService.getPaymentMethod(1L, 10L))
                    .thenReturn(aPaymentMethodResponse(10L));

            mvc.perform(get(BASE + "/{pmId}", 1L, 10L)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.cardType").value("VISA"))
                    .andExpect(jsonPath("$.last4").value("4242"))
                    .andExpect(jsonPath("$.cardholderName").value("Alice Smith"));
        }

        @Test
        @DisplayName("card not found — 404 with PaymentMethodNotFound error")
        void getPaymentMethod_NotFound_Returns404() throws Exception {
            when(paymentMethodService.getPaymentMethod(1L, 999L))
                    .thenThrow(new PaymentMethodNotFoundException(999L));

            mvc.perform(get(BASE + "/{pmId}", 1L, 999L)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PaymentMethodNotFound"));
        }

        @Test
        @DisplayName("IDOR — different user gets 404")
        void getPaymentMethod_IDOR_Returns404() throws Exception {
            // Service returns 404 when userId does not own pmId
            when(paymentMethodService.getPaymentMethod(2L, 10L))
                    .thenThrow(new PaymentMethodNotFoundException(10L));

            mvc.perform(get(BASE + "/{pmId}", 1L, 10L)
                            .with(authentication(customerAuth(2L)))) // user 2 accessing user 1's resource
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PaymentMethodNotFound"));
        }

        @Test
        @DisplayName("unauthenticated — 401")
        void getPaymentMethod_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BASE + "/{pmId}", 1L, 10L))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // PATCH /api/v1/users/{userId}/payment-methods/{pmId}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/users/{userId}/payment-methods/{pmId} — setDefault")
    class SetDefault {

        @Test
        @DisplayName("owner sets card as default — 200 with updated card")
        void setDefault_Owner_Returns200() throws Exception {
            PaymentMethodResponse updated = new PaymentMethodResponse(
                    10L, CardType.VISA, "4242", 6, FUTURE_YEAR, "Alice Smith", true,
                    Instant.parse("2026-01-01T10:00:00Z"));
            when(paymentMethodService.setDefault(eq(1L), eq(10L), any()))
                    .thenReturn(updated);

            mvc.perform(patch(BASE + "/{pmId}", 1L, 10L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "isDefault": true }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.isDefault").value(true));
        }

        @Test
        @DisplayName("isDefault=false — 422 with InvalidDefaultToggle error")
        void setDefault_IsDefaultFalse_Returns422() throws Exception {
            when(paymentMethodService.setDefault(eq(1L), eq(10L), any()))
                    .thenThrow(new InvalidDefaultToggleException());

            mvc.perform(patch(BASE + "/{pmId}", 1L, 10L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "isDefault": false }
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("InvalidDefaultToggle"));
        }

        @Test
        @DisplayName("card not found — 404 with PaymentMethodNotFound error")
        void setDefault_NotFound_Returns404() throws Exception {
            when(paymentMethodService.setDefault(eq(1L), eq(999L), any()))
                    .thenThrow(new PaymentMethodNotFoundException(999L));

            mvc.perform(patch(BASE + "/{pmId}", 1L, 999L)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "isDefault": true }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PaymentMethodNotFound"));
        }

        @Test
        @DisplayName("unauthenticated — 401")
        void setDefault_Unauthenticated_Returns401() throws Exception {
            mvc.perform(patch(BASE + "/{pmId}", 1L, 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "isDefault": true }
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/users/{userId}/payment-methods/{pmId}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/users/{userId}/payment-methods/{pmId} — deletePaymentMethod")
    class DeletePaymentMethod {

        @Test
        @DisplayName("owner deletes own card — 204")
        void deletePaymentMethod_Owner_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/{pmId}", 1L, 10L)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNoContent());

            verify(paymentMethodService).deletePaymentMethod(1L, 10L);
        }

        @Test
        @DisplayName("admin deletes any card — 204")
        void deletePaymentMethod_Admin_Returns204() throws Exception {
            mvc.perform(delete(BASE + "/{pmId}", 1L, 10L)
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());

            verify(paymentMethodService).deletePaymentMethod(1L, 10L);
        }

        @Test
        @DisplayName("card not found — 404 with PaymentMethodNotFound error")
        void deletePaymentMethod_NotFound_Returns404() throws Exception {
            doThrow(new PaymentMethodNotFoundException(999L))
                    .when(paymentMethodService).deletePaymentMethod(1L, 999L);

            mvc.perform(delete(BASE + "/{pmId}", 1L, 999L)
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PaymentMethodNotFound"));
        }

        @Test
        @DisplayName("unauthenticated — 401")
        void deletePaymentMethod_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(BASE + "/{pmId}", 1L, 10L))
                    .andExpect(status().isUnauthorized());
        }
    }
}
