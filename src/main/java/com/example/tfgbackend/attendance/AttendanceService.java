package com.example.tfgbackend.attendance;

import com.example.tfgbackend.attendance.dto.AttendanceResponse;
import com.example.tfgbackend.attendance.dto.RecordAttendanceRequest;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for recording and querying class attendance.
 *
 * <p>Stub — full implementation to follow in the green phase.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    /**
     * Bulk-upsert attendance records for a session.
     *
     * <p>Rules:
     * <ul>
     *   <li>Session must exist — throws {@code SessionNotFoundException} if not.</li>
     *   <li>Session must be FINISHED or ACTIVE — throws {@code SessionNotAttendableException}
     *       for SCHEDULED or CANCELLED.</li>
     *   <li>Each userId must exist — throws {@code UserNotFoundException} on first missing user.</li>
     *   <li>If a record already exists for (user, session) it is updated (upsert semantics).</li>
     *   <li>Links to an existing {@code Booking} for that user+session when one is found.</li>
     * </ul>
     *
     * @param sessionId the session to record attendance for
     * @param request   the list of (userId, status) entries
     * @return the created or updated attendance responses
     */
    @Transactional
    public List<AttendanceResponse> recordAttendance(Long sessionId, RecordAttendanceRequest request) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Returns all attendance records for a session.
     *
     * @param sessionId the session
     * @return list of attendance responses; empty list if no records yet
     */
    public List<AttendanceResponse> getSessionAttendance(Long sessionId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Deletes a single attendance record.
     *
     * @param sessionId    the session the record must belong to
     * @param attendanceId the record to delete
     */
    @Transactional
    public void deleteAttendance(Long sessionId, Long attendanceId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
