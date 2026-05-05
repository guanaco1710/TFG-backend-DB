package com.example.tfgbackend.subscription;

import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySchedulerTest {

    @Mock SubscriptionRepository subscriptionRepository;

    @InjectMocks SubscriptionExpiryScheduler scheduler;

    private Subscription pendingCancellation;

    @BeforeEach
    void setUp() throws Exception {
        User customer = User.builder()
                .name("Alice").email("alice@test.com")
                .passwordHash("hash").role(UserRole.CUSTOMER).active(true).build();
        MembershipPlan plan = MembershipPlan.builder()
                .name("Gold").priceMonthly(new BigDecimal("49.99"))
                .classesPerMonth(20).allowsWaitlist(true).active(true).durationMonths(1).build();
        Gym gym = Gym.builder()
                .name("FitZone").address("Calle Mayor 1").city("Madrid")
                .phone("+34 911 000 001").openingHours("07:00-22:00").active(true).build();

        pendingCancellation = Subscription.builder()
                .user(customer).plan(plan).gym(gym)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusMonths(1))
                .renewalDate(LocalDate.now().minusDays(1))
                .cancelledAt(Instant.now().minusSeconds(3600))
                .classesUsedThisMonth(5).build();

        var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(pendingCancellation, 100L);
    }

    @Test
    @DisplayName("subscriptions past renewalDate with cancelledAt set — status flipped to CANCELLED")
    void expirePendingCancellations_ExpiredSubscriptionsExist_SetsStatusCancelled() {
        when(subscriptionRepository.findExpiredPendingCancellations(any()))
                .thenReturn(List.of(pendingCancellation));
        when(subscriptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.expirePendingCancellations();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Subscription>> captor = ArgumentCaptor.forClass(List.class);
        verify(subscriptionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("no expired subscriptions — saveAll never called")
    void expirePendingCancellations_NothingExpired_SaveAllNotCalled() {
        when(subscriptionRepository.findExpiredPendingCancellations(any())).thenReturn(List.of());

        scheduler.expirePendingCancellations();

        verify(subscriptionRepository, never()).saveAll(any());
    }
}
