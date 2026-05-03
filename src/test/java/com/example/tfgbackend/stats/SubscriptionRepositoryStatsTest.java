package com.example.tfgbackend.stats;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.subscription.Subscription;
import com.example.tfgbackend.subscription.SubscriptionRepository;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests for the stats-specific query method added to
 * {@link SubscriptionRepository}.
 *
 * Written before the query method exists (TDD).
 */
class SubscriptionRepositoryStatsTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired SubscriptionRepository repository;

    private User alice;
    private MembershipPlan basicPlan;
    private MembershipPlan premiumPlan;
    private Gym gym;

    @BeforeEach
    void setUp() {
        alice = em.persistAndFlush(User.builder()
                .name("Alice").email("alice@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());

        basicPlan = em.persistAndFlush(MembershipPlan.builder()
                .name("Basic").priceMonthly(BigDecimal.valueOf(20))
                .classesPerMonth(10).durationMonths(1).build());

        premiumPlan = em.persistAndFlush(MembershipPlan.builder()
                .name("Premium").priceMonthly(BigDecimal.valueOf(50))
                .classesPerMonth(null).durationMonths(1).build());

        gym = em.persistAndFlush(Gym.builder()
                .name("FitZone").address("Calle Mayor 1").city("Madrid").build());
    }

    private Subscription persist(User user, MembershipPlan plan, SubscriptionStatus status, LocalDate startDate) {
        return em.persistAndFlush(Subscription.builder()
                .user(user).plan(plan).gym(gym).status(status)
                .startDate(startDate)
                .renewalDate(startDate.plusMonths(1))
                .build());
    }

    // -------------------------------------------------------------------------
    // findTopByUserIdAndStatusOrderByStartDateDesc
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("returns the most recent active subscription when multiple exist")
    void findTop_MultipleActive_ReturnsMostRecentByStartDate() {
        LocalDate older = LocalDate.now().minusMonths(2);
        LocalDate newer = LocalDate.now().minusMonths(1);
        persist(alice, basicPlan, SubscriptionStatus.ACTIVE, older);
        persist(alice, premiumPlan, SubscriptionStatus.ACTIVE, newer);
        em.clear();

        Optional<Subscription> result = repository.findTopByUserIdAndStatusOrderByStartDateDesc(
                alice.getId(), SubscriptionStatus.ACTIVE);

        assertThat(result).isPresent();
        assertThat(result.get().getPlan().getName()).isEqualTo("Premium");
        assertThat(result.get().getStartDate()).isEqualTo(newer);
    }

    @Test
    @DisplayName("returns empty when user has no active subscription")
    void findTop_NoActiveSubscription_ReturnsEmpty() {
        persist(alice, basicPlan, SubscriptionStatus.CANCELLED, LocalDate.now().minusMonths(1));
        em.clear();

        Optional<Subscription> result = repository.findTopByUserIdAndStatusOrderByStartDateDesc(
                alice.getId(), SubscriptionStatus.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ignores subscriptions belonging to other users")
    void findTop_OtherUserSubscription_ReturnsEmpty() {
        User bob = em.persistAndFlush(User.builder()
                .name("Bob").email("bob@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());
        persist(bob, basicPlan, SubscriptionStatus.ACTIVE, LocalDate.now());
        em.clear();

        Optional<Subscription> result = repository.findTopByUserIdAndStatusOrderByStartDateDesc(
                alice.getId(), SubscriptionStatus.ACTIVE);

        assertThat(result).isEmpty();
    }
}
