package com.example.tfgbackend.subscription;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.common.exception.MembershipPlanInactiveException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.common.exception.SubscriptionAlreadyActiveException;
import com.example.tfgbackend.common.exception.SubscriptionCancellationPendingException;
import com.example.tfgbackend.common.exception.SubscriptionNotActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotFoundException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.gym.GymRepository;
import com.example.tfgbackend.membershipplan.MembershipPlan;
import com.example.tfgbackend.membershipplan.MembershipPlanRepository;
import com.example.tfgbackend.subscription.dto.GymSummary;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final GymRepository gymRepository;

    @Transactional
    public SubscriptionResponse subscribe(Long userId, Long planId, Long gymId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        MembershipPlan plan = membershipPlanRepository.findById(planId)
                .orElseThrow(() -> new MembershipPlanNotFoundException(planId));
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new GymNotFoundException(gymId));

        if (!plan.isActive()) {
            throw new MembershipPlanInactiveException(planId);
        }

        subscriptionRepository.findByUserIdAndGymIdAndStatus(userId, gymId, SubscriptionStatus.ACTIVE)
                .ifPresent(s -> { throw new SubscriptionAlreadyActiveException(userId); });

        LocalDate today = LocalDate.now();
        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .gym(gym)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(today)
                .renewalDate(today.plusMonths(plan.getDurationMonths()))
                .classesUsedThisMonth(0)
                .build();

        return toResponse(subscriptionRepository.save(subscription));
    }

    public List<SubscriptionResponse> getMySubscriptions(Long userId) {
        return subscriptionRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
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
        if (sub.getCancelledAt() != null) {
            throw new SubscriptionCancellationPendingException(subscriptionId);
        }

        sub.setCancelledAt(Instant.now());
        subscriptionRepository.save(sub);
    }

    @Transactional
    public SubscriptionResponse renewSubscription(Long subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (sub.getCancelledAt() != null) {
            throw new SubscriptionCancellationPendingException(subscriptionId);
        }

        if (sub.getPendingPlan() != null) {
            sub.setPlan(sub.getPendingPlan());
            sub.setPendingPlan(null);
        }

        sub.setRenewalDate(sub.getRenewalDate().plusMonths(sub.getPlan().getDurationMonths()));
        sub.setClassesUsedThisMonth(0);
        return toResponse(subscriptionRepository.save(sub));
    }

    @Transactional
    public SubscriptionResponse upgradeSubscription(Long subscriptionId, Long newPlanId, Long requestingUserId, boolean isAdmin) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (!isAdmin && !sub.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Access denied to subscription " + subscriptionId);
        }

        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionNotActiveException(subscriptionId);
        }

        MembershipPlan newPlan = membershipPlanRepository.findById(newPlanId)
                .orElseThrow(() -> new MembershipPlanNotFoundException(newPlanId));

        if (!newPlan.isActive()) {
            throw new MembershipPlanInactiveException(newPlanId);
        }

        sub.setPendingPlan(newPlan);
        return toResponse(subscriptionRepository.save(sub));
    }

    public PageResponse<SubscriptionResponse> getAllSubscriptions(Long userId, SubscriptionStatus status, Pageable pageable) {
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
        return PageResponse.of(page.map(this::toResponse));
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        MembershipPlan plan = sub.getPlan();
        MembershipPlanSummary planSummary = new MembershipPlanSummary(
                plan.getId(),
                plan.getName(),
                plan.getPriceMonthly()
        );
        Gym gym = sub.getGym();
        GymSummary gymSummary = new GymSummary(gym.getId(), gym.getName(), gym.getAddress(), gym.getCity());
        Integer classesPerMonth = plan.getClassesPerMonth();
        Integer remaining = classesPerMonth != null
                ? classesPerMonth - sub.getClassesUsedThisMonth()
                : null;
        boolean pendingCancellation = sub.getCancelledAt() != null
                && sub.getStatus() == SubscriptionStatus.ACTIVE;
        MembershipPlanSummary pendingPlanSummary = sub.getPendingPlan() != null
                ? new MembershipPlanSummary(
                        sub.getPendingPlan().getId(),
                        sub.getPendingPlan().getName(),
                        sub.getPendingPlan().getPriceMonthly())
                : null;
        return new SubscriptionResponse(
                sub.getId(),
                planSummary,
                gymSummary,
                sub.getStatus(),
                sub.getStartDate(),
                sub.getRenewalDate(),
                sub.getClassesUsedThisMonth(),
                remaining,
                pendingCancellation,
                sub.getCancelledAt(),
                pendingPlanSummary
        );
    }
}
