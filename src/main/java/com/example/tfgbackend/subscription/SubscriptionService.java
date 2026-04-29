package com.example.tfgbackend.subscription;

import com.example.tfgbackend.common.exception.MembershipPlanInactiveException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.common.exception.NoActiveSubscriptionException;
import com.example.tfgbackend.common.exception.SubscriptionAlreadyActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotFoundException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.membershipplan.MembershipPlanRepository;
import com.example.tfgbackend.subscription.dto.MembershipPlanSummary;
import com.example.tfgbackend.subscription.dto.SubscriptionResponse;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;

    @Transactional
    public SubscriptionResponse subscribe(Long userId, Long planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new MembershipPlanNotFoundException(planId));

        if (!plan.isActive()) {
            throw new MembershipPlanInactiveException(planId);
        }

        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(s -> { throw new SubscriptionAlreadyActiveException(userId); });

        LocalDate today = LocalDate.now();
        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(today)
                .renewalDate(today.plusMonths(plan.getDurationMonths()))
                .classesUsedThisMonth(0)
                .build();

        return toResponse(subscriptionRepository.save(subscription));
    }

    public SubscriptionResponse getMyActiveSubscription(Long userId) {
        Subscription sub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new NoActiveSubscriptionException(userId));
        return toResponse(sub);
    }

    @Transactional
    public void cancelSubscription(Long subscriptionId, Long requestingUserId, Boolean isAdmin) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (!Boolean.TRUE.equals(isAdmin) && !sub.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Access denied to subscription " + subscriptionId);
        }

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionNotActiveException(subscriptionId);
        }

        sub.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(sub);
    }

    @Transactional
    public SubscriptionResponse renewSubscription(Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        sub.setRenewalDate(sub.getRenewalDate().plusMonths(sub.getPlan().getDurationMonths()));
        sub.setClassesUsedThisMonth(0);
        return toResponse(subscriptionRepository.save(sub));
    }

    public Page<SubscriptionResponse> getAllSubscriptions(Long userId, SubscriptionStatus status, Pageable pageable) {
        Page<Subscription> page;
        if (userId != null && status != null) {
            page = subscriptionRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (userId != null) {
            page = subscriptionRepository.findByUserId(userId, pageable);
        } else if (status != null) {
            page = subscriptionRepository.findByStatus(status, pageable);
        } else {
            page = subscriptionRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        MembershipPlan plan = sub.getPlan();
        MembershipPlanSummary planSummary = new MembershipPlanSummary(
                plan.getId(),
                plan.getName(),
                plan.getPriceMonthly()
        );
        Integer classesPerMonth = plan.getClassesPerMonth();
        Integer remaining = classesPerMonth != null
                ? classesPerMonth - sub.getClassesUsedThisMonth()
                : null;
        return new SubscriptionResponse(
                sub.getId(),
                planSummary,
                sub.getStatus(),
                sub.getStartDate(),
                sub.getRenewalDate(),
                sub.getClassesUsedThisMonth(),
                remaining
        );
    }
}
