package com.example.tfgbackend.attendance;

import com.example.tfgbackend.attendance.dto.AttendanceResponse;
import com.example.tfgbackend.attendance.dto.RecordAttendanceRequest;
import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.exception.AttendanceNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotAttendableException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final Set<SessionStatus> ATTENDABLE = Set.of(SessionStatus.ACTIVE, SessionStatus.FINISHED);

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public List<AttendanceResponse> recordAttendance(Long sessionId, RecordAttendanceRequest request) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!ATTENDABLE.contains(session.getStatus())) {
            throw new SessionNotAttendableException(sessionId, session.getStatus());
        }

        return request.attendances().stream()
                .map(entry -> {
                    var user = userRepository.findById(entry.userId())
                            .orElseThrow(() -> new UserNotFoundException(entry.userId()));

                    Attendance attendance = attendanceRepository
                            .findByUserIdAndSessionId(entry.userId(), sessionId)
                            .orElseGet(() -> {
                                var booking = bookingRepository
                                        .findByUserIdAndSessionId(entry.userId(), sessionId)
                                        .orElse(null);
                                return Attendance.builder()
                                        .user(user)
                                        .session(session)
                                        .booking(booking)
                                        .build();
                            });

                    attendance.setStatus(entry.status());
                    Attendance saved = attendanceRepository.save(attendance);
                    return toResponse(saved);
                })
                .toList();
    }

    public List<AttendanceResponse> getSessionAttendance(Long sessionId) {
        classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        return attendanceRepository.findBySessionId(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteAttendance(Long sessionId, Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));

        if (!attendance.getSession().getId().equals(sessionId)) {
            throw new AttendanceNotFoundException(attendanceId);
        }

        attendanceRepository.delete(attendance);
    }

    private AttendanceResponse toResponse(Attendance a) {
        return new AttendanceResponse(
                a.getId(),
                a.getUser().getId(),
                a.getSession().getId(),
                a.getStatus(),
                a.getRecordedAt()
        );
    }
}
