package com.example.tfgbackend.membershipplan;

// TODO: implement MembershipPlanService before these tests can go green.

import com.example.tfgbackend.common.exception.MembershipPlanInUseException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanRequest;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanResponse;
import com.example.tfgbackend.subscription.SubscriptionRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link MembershipPlanService}. No Spring context — all collaborators are mocked.
 *
 * <p>The service does not yet exist; this test suite is intentionally written ahead of the
 * implementation (TDD). Compilation will fail until the production classes are created.
 */
@ExtendWith(MockitoExtension.class)
class MembershipPlanServiceTest {

    @Mock MembershipPlanRepository membershipPlanRepository;
    @Mock SubscriptionRepository subscriptionRepository;

    @InjectMocks MembershipPlanService membershipPlanService;

    // ---------------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------------

    private MembershipPlan activePlan;
    private MembershipPlan inactivePlan;

    @BeforeEach
    void setUp() {
        activePlan = MembershipPlan.builder()
                .name("Gold").description("Gold membership").priceMonthly(new BigDecimal("49.99"))
                .classesPerMonth(20).allowsWaitlist(true).active(true).durationMonths(1).build();
        setId(activePlan, 1L);

        inactivePlan = MembershipPlan.builder()
                .name("Silver").description("Silver membership").priceMonthly(new BigDecimal("29.99"))
                .classesPerMonth(10).allowsWaitlist(false).active(false).durationMonths(1).build();
        setId(inactivePlan, 2L);
    }

    // ---------------------------------------------------------------------------
    // getAllPlans
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getAllPlans")
    class GetAllPlans {

        @Test
        @DisplayName("no active filter returns all plans as a page of responses")
        void getAllPlans_NoActiveFilter_ReturnsAllPlans() {
            Page<MembershipPlan> planPage = new PageImpl<>(
                    List.of(activePlan, inactivePlan), PageRequest.of(0, 10), 2);
            when(membershipPlanRepository.findAll(any(Pageable.class))).thenReturn(planPage);

            Page<MembershipPlanResponse> result =
                    membershipPlanService.getAllPlans(null, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).extracting(MembershipPlanResponse::id)
                    .containsExactly(1L, 2L);
            verify(membershipPlanRepository).findAll(any(Pageable.class));
            verify(membershipPlanRepository, never()).findByActiveTrue(any(Pageable.class));
        }

        @Test
        @DisplayName("active=true filter returns only active plans")
        void getAllPlans_ActiveFilterTrue_ReturnsOnlyActivePlans() {
            Page<MembershipPlan> activePage = new PageImpl<>(
                    List.of(activePlan), PageRequest.of(0, 10), 1);
            when(membershipPlanRepository.findByActiveTrue(any(Pageable.class))).thenReturn(activePage);

            Page<MembershipPlanResponse> result =
                    membershipPlanService.getAllPlans(true, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(1L);
            assertThat(result.getContent().get(0).active()).isTrue();
            verify(membershipPlanRepository).findByActiveTrue(any(Pageable.class));
            verify(membershipPlanRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("active=false filter returns only inactive plans")
        void getAllPlans_ActiveFilterFalse_ReturnsOnlyInactivePlans() {
            Page<MembershipPlan> inactivePage = new PageImpl<>(
                    List.of(inactivePlan), PageRequest.of(0, 10), 1);
            when(membershipPlanRepository.findByActiveFalse(any(Pageable.class))).thenReturn(inactivePage);

            Page<MembershipPlanResponse> result =
                    membershipPlanService.getAllPlans(false, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).active()).isFalse();
            verify(membershipPlanRepository).findByActiveFalse(any(Pageable.class));
        }

        @Test
        @DisplayName("response fields are mapped correctly from entity")
        void getAllPlans_NoFilter_ResponseMapsAllFields() {
            Page<MembershipPlan> page = new PageImpl<>(
                    List.of(activePlan), PageRequest.of(0, 10), 1);
            when(membershipPlanRepository.findAll(any(Pageable.class))).thenReturn(page);

            Page<MembershipPlanResponse> result =
                    membershipPlanService.getAllPlans(null, PageRequest.of(0, 10));

            MembershipPlanResponse response = result.getContent().get(0);
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Gold");
            assertThat(response.description()).isEqualTo("Gold membership");
            assertThat(response.priceMonthly()).isEqualByComparingTo(new BigDecimal("49.99"));
            assertThat(response.classesPerMonth()).isEqualTo(20);
            assertThat(response.allowsWaitlist()).isTrue();
            assertThat(response.active()).isTrue();
        }
    }

    // ---------------------------------------------------------------------------
    // getPlanById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getPlanById")
    class GetPlanById {

        @Test
        @DisplayName("happy path: returns response for an existing plan")
        void getPlanById_PlanExists_ReturnsMembershipPlanResponse() {
            when(membershipPlanRepository.findById(1L)).thenReturn(Optional.of(activePlan));

            MembershipPlanResponse response = membershipPlanService.getPlanById(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Gold");
            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("plan not found throws MembershipPlanNotFoundException")
        void getPlanById_PlanNotFound_ThrowsMembershipPlanNotFoundException() {
            when(membershipPlanRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> membershipPlanService.getPlanById(999L))
                    .isInstanceOf(MembershipPlanNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------------
    // createPlan
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createPlan")
    class CreatePlan {

        @Test
        @DisplayName("happy path: saves plan with active=true by default and returns response")
        void createPlan_ValidRequest_SavesPlanWithActiveTrueAndReturnsResponse() {
            MembershipPlanRequest request = new MembershipPlanRequest(
                    "Platinum", "Platinum membership", new BigDecimal("79.99"), null, true, 3);

            when(membershipPlanRepository.save(any(MembershipPlan.class))).thenAnswer(inv -> {
                MembershipPlan saved = inv.getArgument(0);
                setId(saved, 3L);
                return saved;
            });

            MembershipPlanResponse response = membershipPlanService.createPlan(request);

            assertThat(response.id()).isEqualTo(3L);
            assertThat(response.name()).isEqualTo("Platinum");
            assertThat(response.active()).isTrue();
            assertThat(response.classesPerMonth()).isNull();
            assertThat(response.allowsWaitlist()).isTrue();

            ArgumentCaptor<MembershipPlan> captor = ArgumentCaptor.forClass(MembershipPlan.class);
            verify(membershipPlanRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
            assertThat(captor.getValue().getName()).isEqualTo("Platinum");
            assertThat(captor.getValue().getDurationMonths()).isEqualTo(3);
        }

        @Test
        @DisplayName("plan with explicit classesPerMonth is persisted correctly")
        void createPlan_WithClassesPerMonth_PersistsLimit() {
            MembershipPlanRequest request = new MembershipPlanRequest(
                    "Basic", "Basic plan", new BigDecimal("19.99"), 8, false, 1);

            when(membershipPlanRepository.save(any(MembershipPlan.class))).thenAnswer(inv -> {
                MembershipPlan saved = inv.getArgument(0);
                setId(saved, 4L);
                return saved;
            });

            MembershipPlanResponse response = membershipPlanService.createPlan(request);

            assertThat(response.classesPerMonth()).isEqualTo(8);
            assertThat(response.allowsWaitlist()).isFalse();

            ArgumentCaptor<MembershipPlan> captor = ArgumentCaptor.forClass(MembershipPlan.class);
            verify(membershipPlanRepository).save(captor.capture());
            assertThat(captor.getValue().getClassesPerMonth()).isEqualTo(8);
        }
    }

    // ---------------------------------------------------------------------------
    // updatePlan
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("updatePlan")
    class UpdatePlan {

        @Test
        @DisplayName("happy path: updates all mutable fields and returns updated response")
        void updatePlan_PlanExists_UpdatesFieldsAndReturnsResponse() {
            when(membershipPlanRepository.findById(1L)).thenReturn(Optional.of(activePlan));
            when(membershipPlanRepository.save(any(MembershipPlan.class))).thenAnswer(inv -> inv.getArgument(0));

            MembershipPlanRequest request = new MembershipPlanRequest(
                    "Gold Plus", "Updated gold membership", new BigDecimal("59.99"), 25, true, 2);

            MembershipPlanResponse response = membershipPlanService.updatePlan(1L, request);

            assertThat(response.name()).isEqualTo("Gold Plus");
            assertThat(response.description()).isEqualTo("Updated gold membership");
            assertThat(response.priceMonthly()).isEqualByComparingTo(new BigDecimal("59.99"));
            assertThat(response.classesPerMonth()).isEqualTo(25);
            assertThat(response.allowsWaitlist()).isTrue();

            ArgumentCaptor<MembershipPlan> captor = ArgumentCaptor.forClass(MembershipPlan.class);
            verify(membershipPlanRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Gold Plus");
            assertThat(captor.getValue().getDurationMonths()).isEqualTo(2);
        }

        @Test
        @DisplayName("plan not found throws MembershipPlanNotFoundException")
        void updatePlan_PlanNotFound_ThrowsMembershipPlanNotFoundException() {
            when(membershipPlanRepository.findById(999L)).thenReturn(Optional.empty());

            MembershipPlanRequest request = new MembershipPlanRequest(
                    "X", "Y", new BigDecimal("10.00"), null, false, 1);

            assertThatThrownBy(() -> membershipPlanService.updatePlan(999L, request))
                    .isInstanceOf(MembershipPlanNotFoundException.class);

            verify(membershipPlanRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // deactivatePlan
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("deactivatePlan")
    class DeactivatePlan {

        @Test
        @DisplayName("happy path: sets active=false on a plan with no active subscriptions")
        void deactivatePlan_PlanExistsNoActiveSubscriptions_SetsActiveFalse() {
            when(membershipPlanRepository.findById(1L)).thenReturn(Optional.of(activePlan));
            when(subscriptionRepository.existsByPlanIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(false);
            when(membershipPlanRepository.save(any(MembershipPlan.class))).thenAnswer(inv -> inv.getArgument(0));

            membershipPlanService.deactivatePlan(1L);

            ArgumentCaptor<MembershipPlan> captor = ArgumentCaptor.forClass(MembershipPlan.class);
            verify(membershipPlanRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("plan not found throws MembershipPlanNotFoundException")
        void deactivatePlan_PlanNotFound_ThrowsMembershipPlanNotFoundException() {
            when(membershipPlanRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> membershipPlanService.deactivatePlan(999L))
                    .isInstanceOf(MembershipPlanNotFoundException.class);

            verify(membershipPlanRepository, never()).save(any());
        }

        @Test
        @DisplayName("plan with active subscriptions throws MembershipPlanInUseException (409)")
        void deactivatePlan_HasActiveSubscriptions_ThrowsMembershipPlanInUseException() {
            when(membershipPlanRepository.findById(1L)).thenReturn(Optional.of(activePlan));
            when(subscriptionRepository.existsByPlanIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                    .thenReturn(true);

            assertThatThrownBy(() -> membershipPlanService.deactivatePlan(1L))
                    .isInstanceOf(MembershipPlanInUseException.class);

            verify(membershipPlanRepository, never()).save(any());
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
