package com.example.tfgbackend.subscription;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.common.exception.MembershipPlanInactiveException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.common.exception.NoActiveSubscriptionException;
import com.example.tfgbackend.common.exception.SubscriptionAlreadyActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotFoundException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.gym.GymRepository;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.membershipplan.MembershipPlanRepository;
import com.example.tfgbackend.subscription.dto.SubscriptionResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Pure unit tests for {@link SubscriptionService}. No Spring context — all collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock UserRepository userRepository;
    @Mock MembershipPlanRepository membershipPlanRepository;
    @Mock GymRepository gymRepository;

    @InjectMocks SubscriptionService subscriptionService;

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    private User customer;
    private MembershipPlan activePlan;
    private MembershipPlan inactivePlan;
    private Gym gym;
    private Subscription activeSubscription;
    private Subscription cancelledSubscription;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .name("Alice").email("alice@test.com")
                .passwordHash("$2a$12$hash").role(UserRole.CUSTOMER).active(true).build();
        setId(customer, 1L);

        activePlan = MembershipPlan.builder()
                .name("Gold").description("Gold plan").priceMonthly(new BigDecimal("49.99"))
                .classesPerMonth(20).allowsWaitlist(true).active(true).durationMonths(1).build();
        setId(activePlan, 10L);

        inactivePlan = MembershipPlan.builder()
                .name("Bronze").description("Old plan").priceMonthly(new BigDecimal("9.99"))
                .classesPerMonth(5).allowsWaitlist(false).active(false).durationMonths(1).build();
        setId(inactivePlan, 11L);

        gym = Gym.builder()
                .name("FitZone Madrid").address("Calle Mayor 1").city("Madrid")
                .phone("+34 911 000 001").openingHours("07:00-22:00").active(true).build();
        setId(gym, 5L);

        activeSubscription = Subscription.builder()
                .user(customer).plan(activePlan).gym(gym)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(5))
                .renewalDate(LocalDate.now().plusDays(25))
                .classesUsedThisMonth(3).build();
        setId(activeSubscription, 100L);

        cancelledSubscription = Subscription.builder()
                .user(customer).plan(activePlan).gym(gym)
                .status(SubscriptionStatus.CANCELLED)
                .startDate(LocalDate.now().minusMonths(2))
                .renewalDate(LocalDate.now().minusMonths(1))
                .classesUsedThisMonth(0).build();
        setId(cancelledSubscription, 101L);
    }

    // ---------------------------------------------------------------------------
    // subscribe
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("happy path: creates ACTIVE subscription and response has gym populated")
        void subscribe_HappyPath_ReturnsResponseWithGym() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(membershipPlanRepository.findById(10L)).thenReturn(Optional.of(activePlan));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription saved = inv.getArgument(0);
                setId(saved, 200L);
                return saved;
            });

            SubscriptionResponse response = subscriptionService.subscribe(1L, 10L, 5L);

            assertThat(response.id()).isEqualTo(200L);
            assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.startDate()).isEqualTo(LocalDate.now());
            assertThat(response.renewalDate()).isEqualTo(LocalDate.now().plusMonths(1));
            assertThat(response.classesUsedThisMonth()).isZero();

            // gym summary is populated
            assertThat(response.gym()).isNotNull();
            assertThat(response.gym().id()).isEqualTo(5L);
            assertThat(response.gym().name()).isEqualTo("FitZone Madrid");
            assertThat(response.gym().address()).isEqualTo("Calle Mayor 1");
            assertThat(response.gym().city()).isEqualTo("Madrid");

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            Subscription saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(saved.getUser()).isEqualTo(customer);
            assertThat(saved.getPlan()).isEqualTo(activePlan);
            assertThat(saved.getGym()).isEqualTo(gym);
            assertThat(saved.getStartDate()).isEqualTo(LocalDate.now());
            assertThat(saved.getRenewalDate()).isEqualTo(LocalDate.now().plusMonths(activePlan.getDurationMonths()));
            assertThat(saved.getClassesUsedThisMonth()).isZero();
        }

        @Test
        @DisplayName("gym not found throws GymNotFoundException")
        void subscribe_GymNotFound_ThrowsGymNotFoundException() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(membershipPlanRepository.findById(10L)).thenReturn(Optional.of(activePlan));
            when(gymRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.subscribe(1L, 10L, 999L))
                    .isInstanceOf(GymNotFoundException.class)
                    .hasMessageContaining("999");

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("user not found throws UserNotFoundException")
        void subscribe_UserNotFound_ThrowsUserNotFoundException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.subscribe(999L, 10L, 5L))
                    .isInstanceOf(UserNotFoundException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("plan not found throws MembershipPlanNotFoundException")
        void subscribe_PlanNotFound_ThrowsMembershipPlanNotFoundException() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(membershipPlanRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.subscribe(1L, 999L, 5L))
                    .isInstanceOf(MembershipPlanNotFoundException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("inactive plan throws MembershipPlanInactiveException")
        void subscribe_PlanInactive_ThrowsMembershipPlanInactiveException() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(membershipPlanRepository.findById(11L)).thenReturn(Optional.of(inactivePlan));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));

            assertThatThrownBy(() -> subscriptionService.subscribe(1L, 11L, 5L))
                    .isInstanceOf(MembershipPlanInactiveException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("user already has ACTIVE subscription throws SubscriptionAlreadyActiveException")
        void subscribe_AlreadyActiveSubscription_ThrowsSubscriptionAlreadyActiveException() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(membershipPlanRepository.findById(10L)).thenReturn(Optional.of(activePlan));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(activeSubscription));

            assertThatThrownBy(() -> subscriptionService.subscribe(1L, 10L, 5L))
                    .isInstanceOf(SubscriptionAlreadyActiveException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("plan with durationMonths=3 sets renewalDate 3 months ahead")
        void subscribe_PlanWithThreeMonthDuration_SetsRenewalDateCorrectly() {
            MembershipPlan quarterlyPlan = MembershipPlan.builder()
                    .name("Quarterly").priceMonthly(new BigDecimal("39.99"))
                    .allowsWaitlist(true).active(true).durationMonths(3).build();
            setId(quarterlyPlan, 12L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(membershipPlanRepository.findById(12L)).thenReturn(Optional.of(quarterlyPlan));
            when(gymRepository.findById(5L)).thenReturn(Optional.of(gym));
            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.save(any())).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                setId(s, 201L);
                return s;
            });

            SubscriptionResponse response = subscriptionService.subscribe(1L, 12L, 5L);

            assertThat(response.renewalDate()).isEqualTo(LocalDate.now().plusMonths(3));
        }
    }

    // ---------------------------------------------------------------------------
    // getMyActiveSubscription
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getMyActiveSubscription")
    class GetMyActiveSubscription {

        @Test
        @DisplayName("happy path: returns response including gym info")
        void getMyActiveSubscription_HappyPath_ReturnsResponseWithGym() {
            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(activeSubscription));

            SubscriptionResponse response = subscriptionService.getMyActiveSubscription(1L);

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.classesUsedThisMonth()).isEqualTo(3);
            assertThat(response.plan().id()).isEqualTo(10L);
            assertThat(response.plan().name()).isEqualTo("Gold");

            // gym summary must be populated
            assertThat(response.gym()).isNotNull();
            assertThat(response.gym().id()).isEqualTo(5L);
            assertThat(response.gym().name()).isEqualTo("FitZone Madrid");
            assertThat(response.gym().address()).isEqualTo("Calle Mayor 1");
            assertThat(response.gym().city()).isEqualTo("Madrid");
        }

        @Test
        @DisplayName("classesRemainingThisMonth is computed correctly when plan has a limit")
        void getMyActiveSubscription_PlanHasClassLimit_ComputesRemainingCorrectly() {
            // activePlan has classesPerMonth=20, activeSubscription has classesUsedThisMonth=3
            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(activeSubscription));

            SubscriptionResponse response = subscriptionService.getMyActiveSubscription(1L);

            assertThat(response.classesRemainingThisMonth()).isEqualTo(17); // 20 - 3
        }

        @Test
        @DisplayName("classesRemainingThisMonth is null when plan has no class limit (unlimited)")
        void getMyActiveSubscription_UnlimitedPlan_ReturnsNullClassesRemaining() {
            MembershipPlan unlimitedPlan = MembershipPlan.builder()
                    .name("Unlimited").priceMonthly(new BigDecimal("99.99"))
                    .classesPerMonth(null).allowsWaitlist(true).active(true).durationMonths(1).build();
            setId(unlimitedPlan, 13L);

            Subscription unlimitedSub = Subscription.builder()
                    .user(customer).plan(unlimitedPlan).gym(gym)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(LocalDate.now()).renewalDate(LocalDate.now().plusMonths(1))
                    .classesUsedThisMonth(5).build();
            setId(unlimitedSub, 102L);

            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.of(unlimitedSub));

            SubscriptionResponse response = subscriptionService.getMyActiveSubscription(1L);

            assertThat(response.classesRemainingThisMonth()).isNull();
        }

        @Test
        @DisplayName("no active subscription throws NoActiveSubscriptionException")
        void getMyActiveSubscription_NoActiveSubscription_ThrowsNoActiveSubscriptionException() {
            when(subscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.getMyActiveSubscription(1L))
                    .isInstanceOf(NoActiveSubscriptionException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // cancelSubscription
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("cancelSubscription")
    class CancelSubscription {

        @Test
        @DisplayName("happy path: owner cancels own active subscription — sets status=CANCELLED")
        void cancelSubscription_OwnerCancelsActiveSubscription_SetsStatusCancelled() {
            when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(activeSubscription));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

            subscriptionService.cancelSubscription(100L, 1L, false);

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        }

        @Test
        @DisplayName("admin cancels another user's subscription — succeeds")
        void cancelSubscription_AdminCancelsAnySubscription_Succeeds() {
            when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(activeSubscription));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Admin userId=99, subscription belongs to customer userId=1
            subscriptionService.cancelSubscription(100L, 99L, true);

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        }

        @Test
        @DisplayName("subscription not found throws SubscriptionNotFoundException")
        void cancelSubscription_SubscriptionNotFound_ThrowsSubscriptionNotFoundException() {
            when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.cancelSubscription(999L, 1L, false))
                    .isInstanceOf(SubscriptionNotFoundException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("already cancelled subscription throws SubscriptionNotActiveException")
        void cancelSubscription_AlreadyCancelled_ThrowsSubscriptionNotActiveException() {
            when(subscriptionRepository.findById(101L)).thenReturn(Optional.of(cancelledSubscription));

            assertThatThrownBy(() -> subscriptionService.cancelSubscription(101L, 1L, false))
                    .isInstanceOf(SubscriptionNotActiveException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired subscription throws SubscriptionNotActiveException")
        void cancelSubscription_ExpiredSubscription_ThrowsSubscriptionNotActiveException() {
            Subscription expiredSub = Subscription.builder()
                    .user(customer).plan(activePlan).gym(gym)
                    .status(SubscriptionStatus.EXPIRED)
                    .startDate(LocalDate.now().minusMonths(2))
                    .renewalDate(LocalDate.now().minusDays(1))
                    .classesUsedThisMonth(0).build();
            setId(expiredSub, 102L);

            when(subscriptionRepository.findById(102L)).thenReturn(Optional.of(expiredSub));

            assertThatThrownBy(() -> subscriptionService.cancelSubscription(102L, 1L, false))
                    .isInstanceOf(SubscriptionNotActiveException.class);

            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("non-admin accessing another user's subscription throws AccessDeniedException")
        void cancelSubscription_NonAdminAccessingOtherUserSubscription_ThrowsAccessDeniedException() {
            when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(activeSubscription));

            // requestingUserId=2, subscription belongs to customer (userId=1), isAdmin=false
            assertThatThrownBy(() -> subscriptionService.cancelSubscription(100L, 2L, false))
                    .isInstanceOf(AccessDeniedException.class);

            verify(subscriptionRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // renewSubscription
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("renewSubscription")
    class RenewSubscription {

        @Test
        @DisplayName("happy path: extends renewalDate by durationMonths and resets classesUsedThisMonth to 0")
        void renewSubscription_ActiveSubscription_ExtendsRenewalAndResetsUsage() {
            LocalDate originalRenewalDate = activeSubscription.getRenewalDate();
            when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(activeSubscription));
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService.renewSubscription(100L);

            assertThat(response.renewalDate())
                    .isEqualTo(originalRenewalDate.plusMonths(activePlan.getDurationMonths()));
            assertThat(response.classesUsedThisMonth()).isZero();

            ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getClassesUsedThisMonth()).isZero();
            assertThat(captor.getValue().getRenewalDate())
                    .isEqualTo(originalRenewalDate.plusMonths(activePlan.getDurationMonths()));
        }

        @Test
        @DisplayName("subscription not found throws SubscriptionNotFoundException")
        void renewSubscription_SubscriptionNotFound_ThrowsSubscriptionNotFoundException() {
            when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.renewSubscription(999L))
                    .isInstanceOf(SubscriptionNotFoundException.class);

            verify(subscriptionRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // getAllSubscriptions
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getAllSubscriptions")
    class GetAllSubscriptions {

        @Test
        @DisplayName("no filters returns PageResponse wrapping all subscriptions")
        void getAllSubscriptions_NoFilters_ReturnsPageResponse() {
            Page<Subscription> page = new PageImpl<>(
                    List.of(activeSubscription, cancelledSubscription), PageRequest.of(0, 10), 2);
            when(subscriptionRepository.findAll(any(Pageable.class))).thenReturn(page);

            PageResponse<SubscriptionResponse> result =
                    subscriptionService.getAllSubscriptions(null, null, PageRequest.of(0, 10));

            assertThat(result).isNotNull();
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).id()).isEqualTo(100L);
            assertThat(result.content().get(1).id()).isEqualTo(101L);
        }

        @Test
        @DisplayName("userId filter returns only subscriptions for that user")
        void getAllSubscriptions_UserIdFilter_ReturnsUserSubscriptions() {
            Page<Subscription> page = new PageImpl<>(
                    List.of(activeSubscription), PageRequest.of(0, 10), 1);
            when(subscriptionRepository.findByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

            PageResponse<SubscriptionResponse> result =
                    subscriptionService.getAllSubscriptions(1L, null, PageRequest.of(0, 10));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("status filter returns only subscriptions with that status")
        void getAllSubscriptions_StatusFilter_ReturnsFilteredSubscriptions() {
            Page<Subscription> page = new PageImpl<>(
                    List.of(activeSubscription), PageRequest.of(0, 10), 1);
            when(subscriptionRepository.findByStatus(eq(SubscriptionStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<SubscriptionResponse> result =
                    subscriptionService.getAllSubscriptions(null, SubscriptionStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).status()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("userId and status filters combined return narrowed results")
        void getAllSubscriptions_UserIdAndStatusFilters_ReturnsCombinedFilteredResults() {
            Page<Subscription> page = new PageImpl<>(
                    List.of(activeSubscription), PageRequest.of(0, 10), 1);
            when(subscriptionRepository.findByUserIdAndStatus(
                    eq(1L), eq(SubscriptionStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<SubscriptionResponse> result =
                    subscriptionService.getAllSubscriptions(1L, SubscriptionStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().get(0).status()).isEqualTo(SubscriptionStatus.ACTIVE);
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void setId(Object entity, Long id) {
        try {
            var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
