package com.example.tfgbackend.classtype;

import com.example.tfgbackend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Template for a class modality (e.g. "Spinning 45 min", "Yoga").
 * One ClassType can be scheduled as many ClassSessions over time.
 *
 * <p>Level is stored as a plain VARCHAR (not a Java enum) because the allowed
 * values are also validated at the DB level via a CHECK constraint in V1 migration,
 * and the set is deliberately small and stable.  If the set grows, promote to enum.
 */
@Entity
@Table(name = "class_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassType extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Difficulty level.  Constrained to BASIC | INTERMEDIATE | ADVANCED by the DB CHECK
     * in {@code V1__create_gymbook_schema.sql}.
     */
    @Column(name = "level", nullable = false, length = 20)
    private String level;
}
