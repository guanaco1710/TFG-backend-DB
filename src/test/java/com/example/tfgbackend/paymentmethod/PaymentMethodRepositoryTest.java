package com.example.tfgbackend.paymentmethod;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test for {@link PaymentMethodRepository}.
 *
 * Uses a real PostgreSQL container (via {@link AbstractRepositoryTest}) — never H2.
 * Covers every custom query method the service will need.
 */
class PaymentMethodRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    PaymentMethodRepository repository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = em.persistAndFlush(User.builder()
                .name("Alice")
                .email("alice@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .build());

        bob = em.persistAndFlush(User.builder()
                .name("Bob")
                .email("bob@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .build());

        em.clear();
    }

    // ---------------------------------------------------------------------------
    // findByUserId
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("user with two cards — returns both, not other users' cards")
        void findByUserId_UserWithCards_ReturnsOnlyTheirCards() {
            // Given
            PaymentMethod pm1 = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());

            PaymentMethod pm2 = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.MASTERCARD)
                    .last4("5555")
                    .expiryMonth(6)
                    .expiryYear(2029)
                    .cardholderName("Alice Smith")
                    .isDefault(false)
                    .user(alice)
                    .build());

            // Bob's card — should NOT appear in alice's query
            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.AMEX)
                    .last4("3782")
                    .expiryMonth(3)
                    .expiryYear(2028)
                    .cardholderName("Bob Jones")
                    .isDefault(true)
                    .user(bob)
                    .build());

            em.clear();

            // When
            List<PaymentMethod> result = repository.findByUserId(alice.getId());

            // Then
            assertThat(result).hasSize(2)
                    .allMatch(pm -> pm.getUser().getId().equals(alice.getId()));
            assertThat(result).extracting(PaymentMethod::getLast4)
                    .containsExactlyInAnyOrder("4242", "5555");
        }

        @Test
        @DisplayName("user with no cards — returns empty list")
        void findByUserId_UserWithNoCards_ReturnsEmpty() {
            // When
            List<PaymentMethod> result = repository.findByUserId(alice.getId());

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // findByIdAndUserId
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("matching id and userId — returns the card")
        void findByIdAndUserId_Match_ReturnsCard() {
            // Given
            PaymentMethod pm = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());
            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findByIdAndUserId(pm.getId(), alice.getId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getLast4()).isEqualTo("4242");
            assertThat(result.get().getUser().getId()).isEqualTo(alice.getId());
        }

        @Test
        @DisplayName("id exists but belongs to different user — returns empty (IDOR protection)")
        void findByIdAndUserId_WrongUser_ReturnsEmpty() {
            // Given — alice's card requested by bob
            PaymentMethod pm = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());
            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findByIdAndUserId(pm.getId(), bob.getId());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("id does not exist — returns empty")
        void findByIdAndUserId_NotFound_ReturnsEmpty() {
            // When
            Optional<PaymentMethod> result = repository.findByIdAndUserId(9999L, alice.getId());

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // existsByUserIdAndCardTypeAndLast4
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("existsByUserIdAndCardTypeAndLast4")
    class ExistsByUserIdAndCardTypeAndLast4 {

        @Test
        @DisplayName("card with same userId, cardType, and last4 exists — returns true")
        void existsByUserIdAndCardTypeAndLast4_DuplicateExists_ReturnsTrue() {
            // Given
            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());
            em.clear();

            // When
            boolean exists = repository.existsByUserIdAndCardTypeAndLast4(
                    alice.getId(), CardType.VISA, "4242");

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("same cardType + last4 but different user — returns false")
        void existsByUserIdAndCardTypeAndLast4_DifferentUser_ReturnsFalse() {
            // Given — alice has a VISA 4242, querying for bob
            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());
            em.clear();

            // When
            boolean exists = repository.existsByUserIdAndCardTypeAndLast4(
                    bob.getId(), CardType.VISA, "4242");

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("same userId + last4 but different cardType — returns false")
        void existsByUserIdAndCardTypeAndLast4_DifferentCardType_ReturnsFalse() {
            // Given
            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());
            em.clear();

            // When
            boolean exists = repository.existsByUserIdAndCardTypeAndLast4(
                    alice.getId(), CardType.MASTERCARD, "4242");

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("no card exists — returns false")
        void existsByUserIdAndCardTypeAndLast4_NoCard_ReturnsFalse() {
            // When
            boolean exists = repository.existsByUserIdAndCardTypeAndLast4(
                    alice.getId(), CardType.VISA, "4242");

            // Then
            assertThat(exists).isFalse();
        }
    }

    // ---------------------------------------------------------------------------
    // findFirstByUserIdAndIsDefaultTrue
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findFirstByUserIdAndIsDefaultTrue")
    class FindFirstByUserIdAndIsDefaultTrue {

        @Test
        @DisplayName("user has a default card — returns it")
        void findFirstByUserIdAndIsDefaultTrue_HasDefault_ReturnsIt() {
            // Given
            PaymentMethod defaultPm = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());

            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.MASTERCARD)
                    .last4("5555")
                    .expiryMonth(6)
                    .expiryYear(2029)
                    .cardholderName("Alice Smith")
                    .isDefault(false)
                    .user(alice)
                    .build());

            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIsDefaultTrue(alice.getId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(defaultPm.getId());
            assertThat(result.get().isDefault()).isTrue();
        }

        @Test
        @DisplayName("user has no default card — returns empty")
        void findFirstByUserIdAndIsDefaultTrue_NoDefault_ReturnsEmpty() {
            // Given
            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(false)
                    .user(alice)
                    .build());
            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIsDefaultTrue(alice.getId());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("user has no cards at all — returns empty")
        void findFirstByUserIdAndIsDefaultTrue_NoCards_ReturnsEmpty() {
            // When
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIsDefaultTrue(alice.getId());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("other user's default card — not returned")
        void findFirstByUserIdAndIsDefaultTrue_OtherUserDefault_NotReturned() {
            // Given — bob has a default; alice has none
            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Bob Jones")
                    .isDefault(true)
                    .user(bob)
                    .build());
            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIsDefaultTrue(alice.getId());

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // findFirstByUserIdAndIdNotOrderByCreatedAtDesc
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findFirstByUserIdAndIdNotOrderByCreatedAtDesc")
    class FindFirstByUserIdAndIdNotOrderByCreatedAtDesc {

        @Test
        @DisplayName("user has sibling cards — returns newest (by createdAt DESC)")
        void findFirstByUserIdAndIdNotOrderByCreatedAtDesc_WithSiblings_ReturnsNewest() {
            // Given — two sibling cards; newerPm was inserted later so has later createdAt
            PaymentMethod olderPm = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.MASTERCARD)
                    .last4("5555")
                    .expiryMonth(3)
                    .expiryYear(2028)
                    .cardholderName("Alice Smith")
                    .isDefault(false)
                    .user(alice)
                    .build());

            PaymentMethod newerPm = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.AMEX)
                    .last4("3782")
                    .expiryMonth(6)
                    .expiryYear(2027)
                    .cardholderName("Alice Smith")
                    .isDefault(false)
                    .user(alice)
                    .build());

            // The card being deleted (excluded from search)
            PaymentMethod defaultPm = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());

            em.clear();

            // When — exclude defaultPm (the one being deleted)
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(
                    alice.getId(), defaultPm.getId());

            // Then — should get the more recently created sibling
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(newerPm.getId());
        }

        @Test
        @DisplayName("user has no siblings (only the excluded card) — returns empty")
        void findFirstByUserIdAndIdNotOrderByCreatedAtDesc_NoSiblings_ReturnsEmpty() {
            // Given — only one card; it is the one being excluded
            PaymentMethod onlyCard = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());
            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(
                    alice.getId(), onlyCard.getId());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("other user's cards are not returned as siblings")
        void findFirstByUserIdAndIdNotOrderByCreatedAtDesc_OtherUserCards_NotReturned() {
            // Given — alice's default card being deleted; bob has other cards
            PaymentMethod aliceDefault = em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.VISA)
                    .last4("4242")
                    .expiryMonth(12)
                    .expiryYear(2030)
                    .cardholderName("Alice Smith")
                    .isDefault(true)
                    .user(alice)
                    .build());

            em.persistAndFlush(PaymentMethod.builder()
                    .cardType(CardType.MASTERCARD)
                    .last4("5555")
                    .expiryMonth(6)
                    .expiryYear(2029)
                    .cardholderName("Bob Jones")
                    .isDefault(true)
                    .user(bob)
                    .build());

            em.clear();

            // When
            Optional<PaymentMethod> result = repository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(
                    alice.getId(), aliceDefault.getId());

            // Then
            assertThat(result).isEmpty();
        }
    }
}
