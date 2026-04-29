package com.example.tfgbackend.membershipplan;

import com.example.tfgbackend.common.exception.MembershipPlanInUseException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanRequest;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanResponse;
import com.example.tfgbackend.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipPlanService {

    private final MembershipPlanRepository membershipPlanRepository;
    private final SubscriptionRepository subscriptionRepository;

    public Page<MembershipPlanResponse> getAllPlans(Boolean active, Pageable pageable) {
        Page<MembershipPlan> page;
        if (active == null) {
            page = membershipPlanRepository.findAll(pageable);
        } else if (active) {
            page = membershipPlanRepository.findByActiveTrue(pageable);
        } else {
            page = membershipPlanRepository.findByActiveFalse(pageable);
        }
        return page.map(this::toResponse);
    }

    public MembershipPlanResponse getPlanById(Long id) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new MembershipPlanNotFoundException(id));
        return toResponse(plan);
    }

    @Transactional
    public MembershipPlanResponse createPlan(MembershipPlanRequest req) {
        MembershipPlan plan = MembershipPlan.builder()
                .name(req.name())
                .description(req.description())
                .priceMonthly(req.priceMonthly())
                .classesPerMonth(req.classesPerMonth())
                .allowsWaitlist(req.allowsWaitlist())
                .durationMonths(req.durationMonths())
                .active(true)
                .build();
        return toResponse(membershipPlanRepository.save(plan));
    }

    @Transactional
    public MembershipPlanResponse updatePlan(Long id, MembershipPlanRequest req) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new MembershipPlanNotFoundException(id));
        plan.setName(req.name());
        plan.setDescription(req.description());
        plan.setPriceMonthly(req.priceMonthly());
        plan.setClassesPerMonth(req.classesPerMonth());
        plan.setAllowsWaitlist(req.allowsWaitlist());
        plan.setDurationMonths(req.durationMonths());
        return toResponse(membershipPlanRepository.save(plan));
    }

    @Transactional
    public void deactivatePlan(Long id) {
        MembershipPlan plan = membershipPlanRepository.findById(id)
                .orElseThrow(() -> new MembershipPlanNotFoundException(id));
        // Guard: refuse deactivation if any subscription is currently using this plan
        if (subscriptionRepository.existsByPlanIdAndStatus(id, SubscriptionStatus.ACTIVE)) {
            throw new MembershipPlanInUseException(id);
        }
        plan.setActive(false);
        membershipPlanRepository.save(plan);
    }

    private MembershipPlanResponse toResponse(MembershipPlan plan) {
        return new MembershipPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceMonthly(),
                plan.getClassesPerMonth(),
                plan.isAllowsWaitlist(),
                plan.isActive()
        );
    }
}
