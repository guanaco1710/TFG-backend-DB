package com.example.tfgbackend.paymentmethod;

import com.example.tfgbackend.common.exception.CardExpiredException;
import com.example.tfgbackend.common.exception.DuplicatePaymentMethodException;
import com.example.tfgbackend.common.exception.InvalidDefaultToggleException;
import com.example.tfgbackend.common.exception.PaymentMethodNotFoundException;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.paymentmethod.dto.AddPaymentMethodRequest;
import com.example.tfgbackend.paymentmethod.dto.PaymentMethodResponse;
import com.example.tfgbackend.paymentmethod.dto.SetDefaultRequest;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit test for {@link PaymentMethodService}.
 *
 * No Spring context — all collaborators are mocked.
 * Written TDD-style: tests describe the expected behaviour before the implementation exists.
 */
@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    @Mock
    PaymentMethodRepository paymentMethodRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PaymentMethodService paymentMethodService;

    // ---------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------

    private User alice;
    private User bob;

    private static final int CURRENT_YEAR  = YearMonth.now().getYear();
    private static final int CURRENT_MONTH = YearMonth.now().getMonthValue();
    private static final int FUTURE_YEAR   = CURRENT_YEAR + 2;

    @BeforeEach
    void setUp() {
        alice = buildUser(1L, "Alice", "alice@test.com");
        bob   = buildUser(2L, "Bob",   "bob@test.com");
    }

    // ---------------------------------------------------------------------------
    // addPaymentMethod
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("addPaymentMethod")
    class AddPaymentMethod {

        private final AddPaymentMethodRequest validRequest = new AddPaymentMethodRequest(
                CardType.VISA, "4242", 6, FUTURE_YEAR, "Alice Smith");

        @Test
        @DisplayName("happy path — saves card, returns correct response")
        void addPaymentMethod_ValidRequest_SavesAndReturnsResponse() {
            // Given
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(paymentMethodRepository.existsByUserIdAndCardTypeAndLast4(
                    1L, CardType.VISA, "4242")).thenReturn(false);
            when(paymentMethodRepository.findByUserId(1L)).thenReturn(List.of()); // existing cards → isDefault=true
            when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> {
                PaymentMethod pm = inv.getArgument(0);
                setId(pm, 10L);
                return pm;
            });

            // When
            PaymentMethodResponse response = paymentMethodService.addPaymentMethod(1L, validRequest);

            // Then
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.cardType()).isEqualTo(CardType.VISA);
            assertThat(response.last4()).isEqualTo("4242");
            assertThat(response.expiryMonth()).isEqualTo(6);
            assertThat(response.expiryYear()).isEqualTo(FUTURE_YEAR);
            assertThat(response.cardholderName()).isEqualTo("Alice Smith");
            assertThat(response.isDefault()).isTrue(); // first card → auto-default

            ArgumentCaptor<PaymentMethod> captor = ArgumentCaptor.forClass(PaymentMethod.class);
            verify(paymentMethodRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isEqualTo(alice);
        }

        @Test
        @DisplayName("first card for user — isDefault set to true automatically")
        void addPaymentMethod_FirstCard_IsDefaultTrue() {
            // Given
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(paymentMethodRepository.existsByUserIdAndCardTypeAndLast4(
                    1L, CardType.VISA, "4242")).thenReturn(false);
            when(paymentMethodRepository.findByUserId(1L)).thenReturn(List.of());
            when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> {
                PaymentMethod pm = inv.getArgument(0);
                setId(pm, 10L);
                return pm;
            });

            // When
            PaymentMethodResponse response = paymentMethodService.addPaymentMethod(1L, validRequest);

            // Then
            assertThat(response.isDefault()).isTrue();
            ArgumentCaptor<PaymentMethod> captor = ArgumentCaptor.forClass(PaymentMethod.class);
            verify(paymentMethodRepository).save(captor.capture());
            assertThat(captor.getValue().isDefault()).isTrue();
        }

        @Test
        @DisplayName("second card for user — isDefault stays false")
        void addPaymentMethod_SecondCard_IsDefaultFalse() {
            // Given — user already has one card
            PaymentMethod existingCard = buildPaymentMethod(5L, alice, CardType.MASTERCARD, "1234", true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(paymentMethodRepository.existsByUserIdAndCardTypeAndLast4(
                    1L, CardType.VISA, "4242")).thenReturn(false);
            when(paymentMethodRepository.findByUserId(1L)).thenReturn(List.of(existingCard));
            when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> {
                PaymentMethod pm = inv.getArgument(0);
                setId(pm, 10L);
                return pm;
            });

            // When
            PaymentMethodResponse response = paymentMethodService.addPaymentMethod(1L, validRequest);

            // Then
            assertThat(response.isDefault()).isFalse();
        }

        @Test
        @DisplayName("duplicate cardType + last4 for same user — throws DuplicatePaymentMethodException")
        void addPaymentMethod_DuplicateCard_ThrowsDuplicatePaymentMethodException() {
            // Given
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(paymentMethodRepository.existsByUserIdAndCardTypeAndLast4(
                    1L, CardType.VISA, "4242")).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.addPaymentMethod(1L, validRequest))
                    .isInstanceOf(DuplicatePaymentMethodException.class);

            verify(paymentMethodRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired card (past year) — throws CardExpiredException")
        void addPaymentMethod_ExpiredCardPastYear_ThrowsCardExpiredException() {
            // Given
            AddPaymentMethodRequest expiredRequest = new AddPaymentMethodRequest(
                    CardType.VISA, "4242", 1, CURRENT_YEAR - 1, "Alice Smith");
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.addPaymentMethod(1L, expiredRequest))
                    .isInstanceOf(CardExpiredException.class);

            verify(paymentMethodRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired card (current year, past month) — throws CardExpiredException")
        void addPaymentMethod_ExpiredCardCurrentYearPastMonth_ThrowsCardExpiredException() {
            // Given — month 1 of current year is expired unless we're in January
            // Use a month that is definitely in the past
            int expiredMonth = (CURRENT_MONTH == 1) ? 12 : CURRENT_MONTH - 1;
            int expiredYear  = (CURRENT_MONTH == 1) ? CURRENT_YEAR - 1 : CURRENT_YEAR;

            AddPaymentMethodRequest expiredRequest = new AddPaymentMethodRequest(
                    CardType.VISA, "4242", expiredMonth, expiredYear, "Alice Smith");
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.addPaymentMethod(1L, expiredRequest))
                    .isInstanceOf(CardExpiredException.class);

            verify(paymentMethodRepository, never()).save(any());
        }

        @Test
        @DisplayName("user not found — throws PaymentMethodNotFoundException")
        void addPaymentMethod_UserNotFound_ThrowsPaymentMethodNotFoundException() {
            // Given
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.addPaymentMethod(99L, validRequest))
                    .isInstanceOf(PaymentMethodNotFoundException.class);

            verify(paymentMethodRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // listPaymentMethods
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("listPaymentMethods")
    class ListPaymentMethods {

        @Test
        @DisplayName("user with two cards — returns all cards mapped to response")
        void listPaymentMethods_UserWithCards_ReturnsAllMapped() {
            // Given
            PaymentMethod pm1 = buildPaymentMethod(1L, alice, CardType.VISA, "4242", true);
            PaymentMethod pm2 = buildPaymentMethod(2L, alice, CardType.MASTERCARD, "1234", false);
            when(userRepository.existsById(1L)).thenReturn(true);
            when(paymentMethodRepository.findByUserId(1L)).thenReturn(List.of(pm1, pm2));

            // When
            List<PaymentMethodResponse> result = paymentMethodService.listPaymentMethods(1L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(PaymentMethodResponse::last4)
                    .containsExactlyInAnyOrder("4242", "1234");
        }

        @Test
        @DisplayName("user with no cards — returns empty list")
        void listPaymentMethods_UserWithNoCards_ReturnsEmptyList() {
            // Given
            when(userRepository.existsById(1L)).thenReturn(true);
            when(paymentMethodRepository.findByUserId(1L)).thenReturn(List.of());

            // When
            List<PaymentMethodResponse> result = paymentMethodService.listPaymentMethods(1L);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("user does not exist — throws PaymentMethodNotFoundException (IDOR)")
        void listPaymentMethods_UserNotFound_ThrowsPaymentMethodNotFoundException() {
            // Given
            when(userRepository.existsById(99L)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.listPaymentMethods(99L))
                    .isInstanceOf(PaymentMethodNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // getPaymentMethod
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getPaymentMethod")
    class GetPaymentMethod {

        @Test
        @DisplayName("owner retrieves own card — returns correct response")
        void getPaymentMethod_OwnerRetrievesOwnCard_ReturnsResponse() {
            // Given
            PaymentMethod pm = buildPaymentMethod(10L, alice, CardType.VISA, "4242", true);
            when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(pm));

            // When
            PaymentMethodResponse response = paymentMethodService.getPaymentMethod(1L, 10L);

            // Then
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.cardType()).isEqualTo(CardType.VISA);
            assertThat(response.last4()).isEqualTo("4242");
            assertThat(response.isDefault()).isTrue();
        }

        @Test
        @DisplayName("card not found — throws PaymentMethodNotFoundException")
        void getPaymentMethod_NotFound_ThrowsPaymentMethodNotFoundException() {
            // Given
            when(paymentMethodRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.getPaymentMethod(1L, 999L))
                    .isInstanceOf(PaymentMethodNotFoundException.class);
        }

        @Test
        @DisplayName("card belongs to different user (IDOR) — throws PaymentMethodNotFoundException")
        void getPaymentMethod_CardBelongsToDifferentUser_ThrowsPaymentMethodNotFoundException() {
            // Given — bob's card, requested by alice
            when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.getPaymentMethod(1L, 10L))
                    .isInstanceOf(PaymentMethodNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // setDefault
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("setDefault")
    class SetDefault {

        @Test
        @DisplayName("happy path — sets isDefault=true, clears previous default, returns response")
        void setDefault_ValidRequest_SetsDefaultAndClearsPrevious() {
            // Given
            PaymentMethod newDefault = buildPaymentMethod(20L, alice, CardType.MASTERCARD, "5555", false);
            PaymentMethod oldDefault = buildPaymentMethod(10L, alice, CardType.VISA, "4242", true);
            SetDefaultRequest request = new SetDefaultRequest(true);

            when(paymentMethodRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(newDefault));
            when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(1L))
                    .thenReturn(Optional.of(oldDefault));
            when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PaymentMethodResponse response = paymentMethodService.setDefault(1L, 20L, request);

            // Then
            assertThat(response.isDefault()).isTrue();
            // old default must be cleared
            assertThat(oldDefault.isDefault()).isFalse();
            verify(paymentMethodRepository).save(oldDefault);
            verify(paymentMethodRepository).save(newDefault);
        }

        @Test
        @DisplayName("no previous default card — sets new default without clearing anything")
        void setDefault_NoPreviousDefault_SetsNewDefault() {
            // Given
            PaymentMethod pm = buildPaymentMethod(20L, alice, CardType.MASTERCARD, "5555", false);
            SetDefaultRequest request = new SetDefaultRequest(true);

            when(paymentMethodRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(pm));
            when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(1L))
                    .thenReturn(Optional.empty());
            when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PaymentMethodResponse response = paymentMethodService.setDefault(1L, 20L, request);

            // Then
            assertThat(response.isDefault()).isTrue();
            // Only one save call for the new default
            verify(paymentMethodRepository).save(pm);
        }

        @Test
        @DisplayName("isDefault=false in request — throws InvalidDefaultToggleException")
        void setDefault_IsDefaultFalse_ThrowsInvalidDefaultToggleException() {
            // Given
            SetDefaultRequest request = new SetDefaultRequest(false);

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.setDefault(1L, 10L, request))
                    .isInstanceOf(InvalidDefaultToggleException.class);

            verify(paymentMethodRepository, never()).save(any());
        }

        @Test
        @DisplayName("card not found — throws PaymentMethodNotFoundException")
        void setDefault_CardNotFound_ThrowsPaymentMethodNotFoundException() {
            // Given
            SetDefaultRequest request = new SetDefaultRequest(true);
            when(paymentMethodRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.setDefault(1L, 999L, request))
                    .isInstanceOf(PaymentMethodNotFoundException.class);

            verify(paymentMethodRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // deletePaymentMethod
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("deletePaymentMethod")
    class DeletePaymentMethod {

        @Test
        @DisplayName("delete non-default card — deletes without promotion")
        void deletePaymentMethod_NonDefaultCard_DeletesWithoutPromotion() {
            // Given
            PaymentMethod pm = buildPaymentMethod(10L, alice, CardType.VISA, "4242", false);
            when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(pm));

            // When
            paymentMethodService.deletePaymentMethod(1L, 10L);

            // Then
            verify(paymentMethodRepository).delete(pm);
            verify(paymentMethodRepository, never())
                    .findFirstByUserIdAndIdNotOrderByCreatedAtDesc(any(), any());
        }

        @Test
        @DisplayName("delete default card with sibling — promotes newest sibling")
        void deletePaymentMethod_DefaultCardWithSibling_PromotesNewestSibling() {
            // Given
            PaymentMethod defaultPm = buildPaymentMethod(10L, alice, CardType.VISA, "4242", true);
            PaymentMethod sibling   = buildPaymentMethod(20L, alice, CardType.MASTERCARD, "5555", false);

            when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(defaultPm));
            when(paymentMethodRepository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(1L, 10L))
                    .thenReturn(Optional.of(sibling));
            when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            paymentMethodService.deletePaymentMethod(1L, 10L);

            // Then
            assertThat(sibling.isDefault()).isTrue();
            verify(paymentMethodRepository).save(sibling);
            verify(paymentMethodRepository).delete(defaultPm);
        }

        @Test
        @DisplayName("delete default card with no sibling — just deletes")
        void deletePaymentMethod_DefaultCardNoSibling_JustDeletes() {
            // Given
            PaymentMethod defaultPm = buildPaymentMethod(10L, alice, CardType.VISA, "4242", true);

            when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(defaultPm));
            when(paymentMethodRepository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(1L, 10L))
                    .thenReturn(Optional.empty());

            // When
            paymentMethodService.deletePaymentMethod(1L, 10L);

            // Then
            verify(paymentMethodRepository, never()).save(any());
            verify(paymentMethodRepository).delete(defaultPm);
        }

        @Test
        @DisplayName("card not found — throws PaymentMethodNotFoundException")
        void deletePaymentMethod_CardNotFound_ThrowsPaymentMethodNotFoundException() {
            // Given
            when(paymentMethodRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentMethodService.deletePaymentMethod(1L, 999L))
                    .isInstanceOf(PaymentMethodNotFoundException.class);

            verify(paymentMethodRepository, never()).delete(any(PaymentMethod.class));
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private User buildUser(Long id, String name, String email) {
        User u = User.builder()
                .name(name)
                .email(email)
                .passwordHash("$2a$12$hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
        setId(u, id);
        return u;
    }

    private PaymentMethod buildPaymentMethod(Long id, User user, CardType cardType,
                                             String last4, boolean isDefault) {
        PaymentMethod pm = PaymentMethod.builder()
                .cardType(cardType)
                .last4(last4)
                .expiryMonth(6)
                .expiryYear(FUTURE_YEAR)
                .cardholderName(user.getName())
                .isDefault(isDefault)
                .user(user)
                .build();
        setId(pm, id);
        // set createdAt so ordering tests work
        setCreatedAt(pm, Instant.now().minusSeconds(id * 10));
        return pm;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCreatedAt(Object entity, Instant createdAt) {
        try {
            var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
