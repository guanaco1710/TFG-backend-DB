package com.example.tfgbackend.rating;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.rating.dto.CreateRatingRequest;
import com.example.tfgbackend.rating.dto.RatingResponse;
import com.example.tfgbackend.rating.dto.UpdateRatingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping(RatingController.BASE)
public class RatingController {

    static final String BASE = "/api/v1/ratings";

    private final RatingService ratingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<RatingResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateRatingRequest request) {
        RatingResponse response = ratingService.create(caller.userId(), request);
        URI location = URI.create(BASE + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<RatingResponse> update(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRatingRequest request) {
        return ResponseEntity.ok(ratingService.update(caller.userId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id) {
        ratingService.delete(caller.userId(), caller.role(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<RatingResponse>> listBySession(
            @PathVariable Long sessionId,
            Pageable pageable) {
        return ResponseEntity.ok(ratingService.listBySession(sessionId, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<RatingResponse>> myRatings(
            @AuthenticationPrincipal AuthenticatedUser caller,
            Pageable pageable) {
        return ResponseEntity.ok(ratingService.myRatings(caller.userId(), pageable));
    }
}
