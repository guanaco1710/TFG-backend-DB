package com.example.tfgbackend.paymentmethod;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.paymentmethod.dto.AddPaymentMethodRequest;
import com.example.tfgbackend.paymentmethod.dto.PaymentMethodResponse;
import com.example.tfgbackend.paymentmethod.dto.SetDefaultRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> list(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(paymentMethodService.listPaymentMethods(userId));
    }

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> add(
            @PathVariable Long userId,
            @Valid @RequestBody AddPaymentMethodRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal,
            UriComponentsBuilder ucb) {
        PaymentMethodResponse response = paymentMethodService.addPaymentMethod(userId, req);
        URI location = URI.create("/api/v1/users/" + userId + "/payment-methods/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{pmId}")
    public ResponseEntity<PaymentMethodResponse> get(
            @PathVariable Long userId,
            @PathVariable Long pmId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        // Pass caller's userId so findByIdAndUserId provides IDOR protection
        return ResponseEntity.ok(paymentMethodService.getPaymentMethod(principal.userId(), pmId));
    }

    @PatchMapping("/{pmId}")
    public ResponseEntity<PaymentMethodResponse> setDefault(
            @PathVariable Long userId,
            @PathVariable Long pmId,
            @RequestBody SetDefaultRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(paymentMethodService.setDefault(userId, pmId, req));
    }

    @DeleteMapping("/{pmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long userId,
            @PathVariable Long pmId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        paymentMethodService.deletePaymentMethod(userId, pmId);
    }
}
