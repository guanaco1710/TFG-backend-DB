package com.example.tfgbackend.subscription;

import com.example.tfgbackend.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 1 * * *") // daily at 01:00
    @Transactional
    public void expirePendingCancellations() {
        List<Subscription> expired = subscriptionRepository.findExpiredPendingCancellations(LocalDate.now());
        if (expired.isEmpty()) return;

        expired.forEach(s -> s.setStatus(SubscriptionStatus.CANCELLED));
        subscriptionRepository.saveAll(expired);
        log.info("Expired {} pending-cancellation subscription(s)", expired.size());
    }
}
