package com.example.tfgbackend.attendance;

import com.example.tfgbackend.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findBySessionId(Long sessionId);
    Optional<Attendance> findByUserIdAndSessionId(Long userId, Long sessionId);

    long countByUserIdAndStatus(Long userId, AttendanceStatus status);

    List<Attendance> findAllByUserIdAndStatus(Long userId, AttendanceStatus status);

    /** Eagerly JOIN FETCH session and classType to avoid LazyInitializationException in the service. */
    @Query("""
            SELECT a FROM Attendance a
              JOIN FETCH a.session s
              JOIN FETCH s.classType
             WHERE a.user.id = :userId
             ORDER BY a.recordedAt DESC
            """)
    Page<Attendance> findByUserIdOrderByRecordedAtDesc(@Param("userId") Long userId, Pageable pageable);

    /** Returns the name of the class type the user attended most (ATTENDED status only). */
    @Query("""
            SELECT s.classType.name FROM Attendance a
              JOIN a.session s
             WHERE a.user.id = :userId
               AND a.status = com.example.tfgbackend.enums.AttendanceStatus.ATTENDED
             GROUP BY s.classType.name
             ORDER BY COUNT(a) DESC
             LIMIT 1
            """)
    Optional<String> findFavoriteClassTypeByUserId(@Param("userId") Long userId);
}
