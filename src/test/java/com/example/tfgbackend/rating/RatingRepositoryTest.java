package com.example.tfgbackend.rating;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Repository slice test for {@link RatingRepository}.
 *
 * Uses Testcontainers PostgreSQL (NOT H2) to ensure the JPQL AVG queries
 * and joins behave identically to production.
 */
class RatingRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    RatingRepository repository;

    private User user1;
    private User user2;
    private ClassSession session;
    private ClassType classType;
    private Rating rating1;
    private Rating rating2;

    @BeforeEach
    void setUp() {
        classType = em.persistAndFlush(ClassType.builder()
                .name("Spinning")
                .description("High-intensity cycling")
                .level("INTERMEDIATE")
                .build());

        session = em.persistAndFlush(ClassSession.builder()
                .startTime(LocalDateTime.now().minusDays(1))
                .durationMinutes(45)
                .maxCapacity(12)
                .room("1A")
                .classType(classType)
                .build());

        user1 = em.persistAndFlush(User.builder()
                .name("Alice")
                .email("alice@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .build());

        user2 = em.persistAndFlush(User.builder()
                .name("Bob")
                .email("bob@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .build());

        rating1 = em.persistAndFlush(Rating.builder()
                .score(4)
                .comment("Great session")
                .ratedAt(Instant.now())
                .user(user1)
                .session(session)
                .build());

        rating2 = em.persistAndFlush(Rating.builder()
                .score(2)
                .comment("Not great")
                .ratedAt(Instant.now())
                .user(user2)
                .session(session)
                .build());

        em.clear();
    }

    // ---------------------------------------------------------------------------
    // findBySessionId (paginated overload)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findBySessionId(Long, Pageable)")
    class FindBySessionIdPaged {

        @Test
        @DisplayName("matching sessionId — returns ratings for that session")
        void findBySessionId_MatchingSessionId_ReturnsRatingsForSession() {
            Page<Rating> result = repository.findBySessionId(session.getId(), PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2)
                    .allMatch(r -> r.getSession().getId().equals(session.getId()));
        }

        @Test
        @DisplayName("wrong sessionId — returns empty page")
        void findBySessionId_WrongSessionId_ReturnsEmptyPage() {
            Page<Rating> result = repository.findBySessionId(9999L, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // findByUserId (paginated overload)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserId(Long, Pageable)")
    class FindByUserIdPaged {

        @Test
        @DisplayName("matching userId — returns that user's ratings")
        void findByUserId_MatchingUserId_ReturnsUsersRatings() {
            Page<Rating> result = repository.findByUserId(user1.getId(), PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1)
                    .allMatch(r -> r.getUser().getId().equals(user1.getId()));
            assertThat(result.getContent().get(0).getScore()).isEqualTo(4);
        }

        @Test
        @DisplayName("wrong userId — returns empty page")
        void findByUserId_WrongUserId_ReturnsEmptyPage() {
            Page<Rating> result = repository.findByUserId(9999L, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------------------
    // findByUserIdAndSessionId
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserIdAndSessionId")
    class FindByUserIdAndSessionId {

        @Test
        @DisplayName("existing combination — returns Optional with the rating")
        void findByUserIdAndSessionId_ExistingCombination_ReturnsRating() {
            Optional<Rating> result = repository.findByUserIdAndSessionId(
                    user1.getId(), session.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getScore()).isEqualTo(4);
            assertThat(result.get().getUser().getId()).isEqualTo(user1.getId());
            assertThat(result.get().getSession().getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("non-existing combination — returns empty Optional")
        void findByUserIdAndSessionId_NonExistingCombination_ReturnsEmpty() {
            Optional<Rating> result = repository.findByUserIdAndSessionId(9999L, session.getId());

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // averageScoreBySessionId
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("averageScoreBySessionId")
    class AverageScoreBySessionId {

        @Test
        @DisplayName("session with ratings — returns correct average")
        void averageScoreBySessionId_SessionWithRatings_ReturnsCorrectAverage() {
            // user1 gave score 4, user2 gave score 2 → average = 3.0
            Optional<Double> avg = repository.averageScoreBySessionId(session.getId());

            assertThat(avg).isPresent();
            assertThat(avg.get()).isCloseTo(3.0, within(0.001));
        }

        @Test
        @DisplayName("session with no ratings — returns empty Optional")
        void averageScoreBySessionId_SessionWithNoRatings_ReturnsEmpty() {
            // Persist a fresh session with no ratings
            ClassSession emptySession = em.persistAndFlush(ClassSession.builder()
                    .startTime(LocalDateTime.now().plusDays(5))
                    .durationMinutes(60)
                    .maxCapacity(10)
                    .room("2B")
                    .classType(classType)
                    .build());
            em.clear();

            Optional<Double> avg = repository.averageScoreBySessionId(emptySession.getId());

            assertThat(avg).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------
    // averageScoreByClassTypeId
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("averageScoreByClassTypeId")
    class AverageScoreByClassTypeId {

        @Test
        @DisplayName("class type with sessions that have ratings — returns correct average")
        void averageScoreByClassTypeId_ClassTypeWithRatings_ReturnsCorrectAverage() {
            // Both ratings (score 4 and score 2) belong to session of classType → average = 3.0
            Optional<Double> avg = repository.averageScoreByClassTypeId(classType.getId());

            assertThat(avg).isPresent();
            assertThat(avg.get()).isCloseTo(3.0, within(0.001));
        }

        @Test
        @DisplayName("class type with no ratings — returns empty Optional")
        void averageScoreByClassTypeId_ClassTypeWithNoRatings_ReturnsEmpty() {
            ClassType emptyClassType = em.persistAndFlush(ClassType.builder()
                    .name("Yoga")
                    .description("Flexibility")
                    .level("BASIC")
                    .build());
            em.clear();

            Optional<Double> avg = repository.averageScoreByClassTypeId(emptyClassType.getId());

            assertThat(avg).isEmpty();
        }
    }
}
