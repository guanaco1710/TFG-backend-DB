package com.example.tfgbackend.subscription;

import com.example.tfgbackend.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);
    Page<Subscription> findByUserId(Long userId, Pageable pageable);
    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);
    Page<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status, Pageable pageable);
    boolean existsByPlanIdAndStatus(Long planId, SubscriptionStatus status);

    Optional<Subscription> findTopByUserIdAndStatusOrderByStartDateDesc(Long userId, SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.cancelledAt IS NOT NULL AND s.renewalDate <= :today")
    List<Subscription> findExpiredPendingCancellations(LocalDate today);
}
