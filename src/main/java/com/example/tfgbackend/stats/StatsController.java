package com.example.tfgbackend.stats;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.stats.dto.AttendanceHistoryEntry;
import com.example.tfgbackend.stats.dto.UserStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/me")
    public ResponseEntity<UserStatsResponse> getUserStats(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(statsService.getUserStats(principal.userId()));
    }

    @GetMapping("/me/history")
    public ResponseEntity<PageResponse<AttendanceHistoryEntry>> getAttendanceHistory(
            Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(statsService.getAttendanceHistory(principal.userId(), pageable));
    }
}
