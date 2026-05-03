package com.example.tfgbackend.subscription;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.enums.SubscriptionStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.subscription.dto.CreateSubscriptionRequest;
import com.example.tfgbackend.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<SubscriptionResponse>> getAllSubscriptions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) SubscriptionStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(subscriptionService.getAllSubscriptions(userId, status, pageable));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        SubscriptionResponse response = subscriptionService.subscribe(
                principal.userId(), request.membershipPlanId(), request.gymId());
        URI location = URI.create("/api/v1/subscriptions/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionResponse> getMyActiveSubscription(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(subscriptionService.getMyActiveSubscription(principal.userId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<Void> cancelSubscription(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Boolean isAdmin = principal.role() == UserRole.ADMIN;
        subscriptionService.cancelSubscription(id, principal.userId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionResponse> renewSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.renewSubscription(id));
    }
}
