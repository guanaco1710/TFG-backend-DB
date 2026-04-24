package com.example.tfgbackend.classsession;

import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.common.BaseEntity;
import com.example.tfgbackend.enums.SessionStatus;
import com.example.tfgbackend.instructor.Instructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A concrete scheduled occurrence of a {@link ClassType}.
 *
 * <p>Capacity races (two users booking the last spot simultaneously) are handled
 * with optimistic locking via the {@code version} column inherited from
 * {@link BaseEntity}.  The service layer must catch {@code ObjectOptimisticLockingFailureException}
 * and retry or surface a "class is full" error to the caller.
 *
 * <p>{@code startTime} is stored as {@code TIMESTAMPTZ} in Postgres and mapped to
 * {@link LocalDateTime} here because it represents the wall-clock time of the class
 * in the gym's local timezone.  The application must ensure the timezone is applied
 * consistently (e.g. via {@code @PostConstruct} setting {@code TimeZone.setDefault}).
 */
@Entity
@Table(
    name = "class_session",
    indexes = {
        @Index(name = "idx_class_session_start_time", columnList = "start_time"),
        @Index(name = "idx_class_session_status",     columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSession extends BaseEntity {

    /** Wall-clock start time of the class in the gym's local timezone. */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "room", nullable = false, length = 50)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    /**
     * The type of class being held.  Mandatory — a session cannot exist without
     * knowing what class it is.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_type_id", nullable = false)
    private ClassType classType;

    /**
     * The assigned instructor.  Nullable — a session may be created before an
     * instructor is assigned, or the instructor record may be deleted (ON DELETE SET NULL
     * in the migration).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Instructor instructor;
}
