package com.example.tfgbackend.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** Retrieve an existing rating for editing or duplicate detection. */
    Optional<Rating> findByUserIdAndSessionId(Long userId, Long sessionId);

    /** All ratings for a session — used to compute average score. */
    List<Rating> findBySessionId(Long sessionId);

    /** A user's rating history. */
    List<Rating> findByUserId(Long userId);

    /** Average score for a session — used on the session detail screen. */
    @Query("""
            SELECT AVG(r.score) FROM Rating r
             WHERE r.session.id = :sessionId
            """)
    Optional<Double> averageScoreBySessionId(@Param("sessionId") Long sessionId);

    /** Average score across all sessions of a given class type. */
    @Query("""
            SELECT AVG(r.score) FROM Rating r
             WHERE r.session.classType.id = :classTypeId
            """)
    Optional<Double> averageScoreByClassTypeId(@Param("classTypeId") Long classTypeId);
}
