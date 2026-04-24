package com.example.tfgbackend.rating;

import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.common.BaseEntity;
import com.example.tfgbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's rating of a completed {@link ClassSession}.
 *
 * <p>One rating per user per session is enforced by the unique constraint
 * {@code (user_id, session_id)}.  Score must be between 1 and 5 (enforced by
 * the DB CHECK constraint in V1 and validated at the service layer).
 */
@Entity
@Table(
    name = "rating",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rating_user_session",
        columnNames = {"user_id", "session_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating extends BaseEntity {

    /** Score from 1 (poor) to 5 (excellent). */
    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /** The instant the rating was submitted. */
    @Column(name = "rated_at", nullable = false)
    @Builder.Default
    private Instant ratedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;
}
