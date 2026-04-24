package com.example.tfgbackend.instructor;

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
 * A gym instructor who can lead ClassSessions.
 *
 * <p>Instructors are modelled separately from {@code app_user} because not every
 * instructor needs a login account (at launch), and a User with role INSTRUCTOR
 * may eventually be linked to an Instructor profile by a separate FK — that join
 * will be added when auth lands.
 */
@Entity
@Table(name = "instructor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instructor extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "specialty", length = 50)
    private String specialty;
}
