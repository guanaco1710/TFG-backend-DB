package com.example.tfgbackend.attendance;

import com.example.tfgbackend.attendance.dto.AttendanceResponse;
import com.example.tfgbackend.attendance.dto.RecordAttendanceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * REST endpoints for recording and querying class attendance.
 *
 * <p>Stub — full implementation to follow in the green phase.
 */
@RestController
@RequestMapping("/api/v1/class-sessions/{sessionId}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /** POST /api/v1/class-sessions/{sessionId}/attendance — record bulk attendance (INSTRUCTOR or ADMIN). */
    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ResponseEntity<List<AttendanceResponse>> recordAttendance(
            @PathVariable Long sessionId,
            @Valid @RequestBody RecordAttendanceRequest request,
            UriComponentsBuilder ucb) {
        List<AttendanceResponse> responses = attendanceService.recordAttendance(sessionId, request);
        return ResponseEntity
                .created(ucb.path("/api/v1/class-sessions/{sessionId}/attendance").buildAndExpand(sessionId).toUri())
                .body(responses);
    }

    /** GET /api/v1/class-sessions/{sessionId}/attendance — list attendance for a session (INSTRUCTOR or ADMIN). */
    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ResponseEntity<List<AttendanceResponse>> getSessionAttendance(@PathVariable Long sessionId) {
        return ResponseEntity.ok(attendanceService.getSessionAttendance(sessionId));
    }

    /** DELETE /api/v1/class-sessions/{sessionId}/attendance/{attendanceId} — delete a record (ADMIN only). */
    @DeleteMapping("/{attendanceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAttendance(
            @PathVariable Long sessionId,
            @PathVariable Long attendanceId) {
        attendanceService.deleteAttendance(sessionId, attendanceId);
        return ResponseEntity.noContent().build();
    }
}
