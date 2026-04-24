package com.example.tfgbackend.classsession;

import com.example.tfgbackend.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    /**
     * Upcoming sessions for the schedule view.
     * Fetches classType eagerly to avoid N+1 on listing pages.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
             JOIN FETCH cs.classType
             LEFT JOIN FETCH cs.instructor
             WHERE cs.startTime >= :from
               AND cs.startTime <  :to
               AND cs.status = :status
             ORDER BY cs.startTime ASC
            """)
    List<ClassSession> findSchedule(
            @Param("from")   LocalDateTime from,
            @Param("to")     LocalDateTime to,
            @Param("status") SessionStatus status);

    /** Paginated list for admin — all statuses, optional filter by classType. */
    Page<ClassSession> findByClassTypeId(Long classTypeId, Pageable pageable);

    /** All sessions led by a specific instructor. */
    Page<ClassSession> findByInstructorId(Long instructorId, Pageable pageable);

    /** Count bookable (SCHEDULED) future sessions for a class type. */
    long countByClassTypeIdAndStatusAndStartTimeAfter(
            Long classTypeId, SessionStatus status, LocalDateTime after);
}
