package com.example.tfgbackend.booking;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.booking.dto.BookingResponse;
import com.example.tfgbackend.booking.dto.CreateBookingRequest;
import com.example.tfgbackend.booking.dto.RosterEntryResponse;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.waitlist.dto.WaitlistEntryResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    private static final String BOOKINGS_BASE = "/api/v1/bookings";
    private static final String SESSIONS_BASE = "/api/v1/class-sessions";

    @PostMapping(BOOKINGS_BASE)
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        BookingResponse response = bookingService.createBooking(principal.userId(), request.sessionId());
        URI location = URI.create(BOOKINGS_BASE + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(BOOKINGS_BASE + "/{id}/cancel")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Boolean isAdmin = principal.role() == UserRole.ADMIN;
        bookingService.cancelBooking(id, principal.userId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(BOOKINGS_BASE + "/me")
    public ResponseEntity<PageResponse<BookingResponse>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(bookingService.getMyBookings(principal.userId(), status, pageable));
    }

    @GetMapping(BOOKINGS_BASE + "/me/waitlist")
    public ResponseEntity<List<WaitlistEntryResponse>> getMyWaitlistEntries(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(bookingService.getMyWaitlistEntries(principal.userId()));
    }

    @GetMapping(BOOKINGS_BASE + "/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Boolean isAdmin = principal.role() == UserRole.ADMIN;
        return ResponseEntity.ok(bookingService.getBookingById(id, principal.userId(), isAdmin));
    }

    @GetMapping(SESSIONS_BASE + "/{sessionId}/bookings")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<PageResponse<RosterEntryResponse>> getSessionRoster(
            @PathVariable Long sessionId,
            Pageable pageable) {
        return ResponseEntity.ok(bookingService.getSessionRoster(sessionId, pageable));
    }

    @PostMapping(SESSIONS_BASE + "/{sessionId}/waitlist")
    public ResponseEntity<WaitlistEntryResponse> joinWaitlist(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        WaitlistEntryResponse response = bookingService.joinWaitlist(principal.userId(), sessionId);
        URI location = URI.create(SESSIONS_BASE + "/" + sessionId + "/waitlist/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @DeleteMapping(SESSIONS_BASE + "/{sessionId}/waitlist")
    public ResponseEntity<Void> leaveWaitlist(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        bookingService.leaveWaitlist(principal.userId(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
