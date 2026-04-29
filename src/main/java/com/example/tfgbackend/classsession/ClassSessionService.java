package com.example.tfgbackend.classsession;

import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.dto.ClassSessionRequest;
import com.example.tfgbackend.classsession.dto.ClassSessionResponse;
import com.example.tfgbackend.classsession.dto.ClassTypeSummary;
import com.example.tfgbackend.classsession.dto.GymSummary;
import com.example.tfgbackend.classsession.dto.InstructorSummary;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classtype.ClassTypeRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.common.exception.InstructorNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.gym.Gym;
import com.example.tfgbackend.gym.GymRepository;
import com.example.tfgbackend.instructor.Instructor;
import com.example.tfgbackend.instructor.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassSessionService {

    private final ClassSessionRepository classSessionRepository;
    private final BookingRepository bookingRepository;
    private final ClassTypeRepository classTypeRepository;
    private final GymRepository gymRepository;
    private final InstructorRepository instructorRepository;

    @Transactional
    public ClassSessionResponse createSession(ClassSessionRequest req) {
        ClassType classType = classTypeRepository.findById(req.classTypeId())
                .orElseThrow(() -> new ClassTypeNotFoundException(req.classTypeId()));

        Gym gym = null;
        if (req.gymId() != null) {
            gym = gymRepository.findById(req.gymId())
                    .orElseThrow(() -> new GymNotFoundException(req.gymId()));
        }

        Instructor instructor = null;
        if (req.instructorId() != null) {
            instructor = instructorRepository.findById(req.instructorId())
                    .orElseThrow(() -> new InstructorNotFoundException(req.instructorId()));
        }

        ClassSession session = ClassSession.builder()
                .classType(classType)
                .gym(gym)
                .instructor(instructor)
                .startTime(req.startTime())
                .durationMinutes(req.durationMinutes())
                .maxCapacity(req.maxCapacity())
                .room(req.room())
                .build();

        ClassSession saved = classSessionRepository.save(session);
        return toResponse(saved);
    }

    public ClassSessionResponse getSessionById(Long id) {
        ClassSession session = classSessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        return toResponse(session);
    }

    public List<ClassSessionResponse> getSchedule(LocalDateTime from, LocalDateTime to) {
        return classSessionRepository.findSchedule(from, to, SessionStatus.SCHEDULED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PageResponse<ClassSessionResponse> listSessions(Long classTypeId, Pageable pageable) {
        Page<ClassSession> page;
        if (classTypeId != null) {
            page = classSessionRepository.findByClassTypeId(classTypeId, pageable);
        } else {
            page = classSessionRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    // Must not allow updating a CANCELLED session — that state is terminal.
    @Transactional
    public ClassSessionResponse updateSession(Long id, ClassSessionRequest req) {
        ClassSession session = classSessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new SessionNotBookableException(id);
        }

        ClassType classType = classTypeRepository.findById(req.classTypeId())
                .orElseThrow(() -> new ClassTypeNotFoundException(req.classTypeId()));

        Gym gym = null;
        if (req.gymId() != null) {
            gym = gymRepository.findById(req.gymId())
                    .orElseThrow(() -> new GymNotFoundException(req.gymId()));
        }

        Instructor instructor = null;
        if (req.instructorId() != null) {
            instructor = instructorRepository.findById(req.instructorId())
                    .orElseThrow(() -> new InstructorNotFoundException(req.instructorId()));
        }

        session.setClassType(classType);
        session.setGym(gym);
        session.setInstructor(instructor);
        session.setStartTime(req.startTime());
        session.setDurationMinutes(req.durationMinutes());
        session.setMaxCapacity(req.maxCapacity());
        session.setRoom(req.room());

        ClassSession saved = classSessionRepository.save(session);
        return toResponse(saved);
    }

    @Transactional
    public void cancelSession(Long id) {
        ClassSession session = classSessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new SessionNotBookableException(id);
        }

        session.setStatus(SessionStatus.CANCELLED);
        classSessionRepository.save(session);
    }

    private ClassSessionResponse toResponse(ClassSession session) {
        int confirmed = (int) bookingRepository.countConfirmedBySessionId(session.getId());

        ClassTypeSummary classTypeSummary = new ClassTypeSummary(
                session.getClassType().getId(),
                session.getClassType().getName()
        );

        GymSummary gymSummary = session.getGym() == null ? null : new GymSummary(
                session.getGym().getId(),
                session.getGym().getName(),
                session.getGym().getCity()
        );

        InstructorSummary instructorSummary = session.getInstructor() == null ? null : new InstructorSummary(
                session.getInstructor().getId(),
                session.getInstructor().getName(),
                session.getInstructor().getSpecialty()
        );

        return new ClassSessionResponse(
                session.getId(),
                classTypeSummary,
                gymSummary,
                instructorSummary,
                session.getStartTime(),
                session.getDurationMinutes(),
                session.getMaxCapacity(),
                session.getRoom(),
                session.getStatus(),
                confirmed,
                session.getMaxCapacity() - confirmed
        );
    }
}
