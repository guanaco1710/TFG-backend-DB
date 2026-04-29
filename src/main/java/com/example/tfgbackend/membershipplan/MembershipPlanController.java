package com.example.tfgbackend.membershipplan;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanRequest;
import com.example.tfgbackend.membershipplan.dto.MembershipPlanResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/membership-plans")
@RequiredArgsConstructor
public class MembershipPlanController {

    private final MembershipPlanService membershipPlanService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<Page<MembershipPlanResponse>> getAllPlans(
            @RequestParam(required = false) Boolean active,
            @AuthenticationPrincipal AuthenticatedUser principal,
            Pageable pageable) {
        // Customers always see only active plans regardless of query param
        Boolean effectiveActive = principal.role() == UserRole.CUSTOMER ? Boolean.TRUE : active;
        return ResponseEntity.ok(membershipPlanService.getAllPlans(effectiveActive, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<MembershipPlanResponse> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(membershipPlanService.getPlanById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipPlanResponse> createPlan(
            @Valid @RequestBody MembershipPlanRequest request) {
        MembershipPlanResponse response = membershipPlanService.createPlan(request);
        URI location = URI.create("/api/v1/membership-plans/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipPlanResponse> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody MembershipPlanRequest request) {
        return ResponseEntity.ok(membershipPlanService.updatePlan(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivatePlan(@PathVariable Long id) {
        membershipPlanService.deactivatePlan(id);
        return ResponseEntity.noContent().build();
    }
}
