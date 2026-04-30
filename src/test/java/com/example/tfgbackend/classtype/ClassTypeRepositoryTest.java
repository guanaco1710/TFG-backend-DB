package com.example.tfgbackend.classtype;

import com.example.tfgbackend.AbstractRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test for {@link ClassTypeRepository}.
 *
 * Uses Testcontainers PostgreSQL (NOT H2) as the JPQL query uses LOWER() and
 * LIKE with CONCAT() that behave correctly only on a real PostgreSQL instance.
 */
class ClassTypeRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    ClassTypeRepository repository;

    private ClassType spinning;
    private ClassType yoga;
    private ClassType crossfit;

    @BeforeEach
    void setUp() {
        // Persist in non-alphabetical order to verify ORDER BY name ASC later
        crossfit = em.persistAndFlush(ClassType.builder()
                .name("Crossfit Power")
                .description("Functional strength training")
                .level("ADVANCED")
                .build());

        yoga = em.persistAndFlush(ClassType.builder()
                .name("Yoga Flow")
                .description("Flexibility and mindfulness")
                .level("BASIC")
                .build());

        spinning = em.persistAndFlush(ClassType.builder()
                .name("Spinning 45min")
                .description("High-intensity cycling")
                .level("INTERMEDIATE")
                .build());

        em.clear();
    }

    // ---------------------------------------------------------------------------
    // findByFilters
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByFilters")
    class FindByFilters {

        @Test
        @DisplayName("no filters — returns all class types")
        void findByFilters_NoFilters_ReturnsAll() {
            Page<ClassType> result = repository.findByFilters(null, null, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("level filter ADVANCED — returns only ADVANCED class types")
        void findByFilters_LevelFilter_ReturnsOnlyMatchingLevel() {
            Page<ClassType> result = repository.findByFilters("ADVANCED", null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .hasSize(1)
                    .allMatch(ct -> ct.getLevel().equals("ADVANCED"));
            assertThat(result.getContent().get(0).getName()).isEqualTo("Crossfit Power");
        }

        @Test
        @DisplayName("q filter matches name substring case-insensitively — returns matching class types")
        void findByFilters_QFilterMatchesName_ReturnsCaseInsensitiveMatch() {
            Page<ClassType> result = repository.findByFilters(null, "spinning", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Spinning 45min");
        }

        @Test
        @DisplayName("both level and q filters — returns intersection only")
        void findByFilters_BothFilters_ReturnsIntersection() {
            Page<ClassType> result = repository.findByFilters("BASIC", "Yoga", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Yoga Flow");
        }

        @Test
        @DisplayName("level filter with no match — returns empty page")
        void findByFilters_LevelNoMatch_ReturnsEmpty() {
            Page<ClassType> result = repository.findByFilters("BEGINNER", null, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("results are ordered by name ASC")
        void findByFilters_NoFilters_ResultsOrderedByNameAsc() {
            Page<ClassType> result = repository.findByFilters(null, null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(ClassType::getName)
                    .containsExactly("Crossfit Power", "Spinning 45min", "Yoga Flow");
        }
    }

    // ---------------------------------------------------------------------------
    // existsByNameIgnoreCase
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("existsByNameIgnoreCase")
    class ExistsByNameIgnoreCase {

        @Test
        @DisplayName("existing name exact case — returns true")
        void existsByNameIgnoreCase_ExactMatch_ReturnsTrue() {
            assertThat(repository.existsByNameIgnoreCase("Yoga Flow")).isTrue();
        }

        @Test
        @DisplayName("existing name different case — returns true")
        void existsByNameIgnoreCase_DifferentCase_ReturnsTrue() {
            assertThat(repository.existsByNameIgnoreCase("yoga flow")).isTrue();
            assertThat(repository.existsByNameIgnoreCase("YOGA FLOW")).isTrue();
        }

        @Test
        @DisplayName("non-existing name — returns false")
        void existsByNameIgnoreCase_NonExistingName_ReturnsFalse() {
            assertThat(repository.existsByNameIgnoreCase("Pilates")).isFalse();
        }
    }

    // ---------------------------------------------------------------------------
    // existsByNameIgnoreCaseAndIdNot
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("existsByNameIgnoreCaseAndIdNot")
    class ExistsByNameIgnoreCaseAndIdNot {

        @Test
        @DisplayName("same name, different id — returns true (conflict with another record)")
        void existsByNameIgnoreCaseAndIdNot_SameNameDifferentId_ReturnsTrue() {
            // yoga has name "Yoga Flow"; checking from spinning's id — should detect conflict
            assertThat(repository.existsByNameIgnoreCaseAndIdNot(
                    "Yoga Flow", spinning.getId())).isTrue();
        }

        @Test
        @DisplayName("same name, same id — returns false (updating itself is not a conflict)")
        void existsByNameIgnoreCaseAndIdNot_SameNameSameId_ReturnsFalse() {
            assertThat(repository.existsByNameIgnoreCaseAndIdNot(
                    "Yoga Flow", yoga.getId())).isFalse();
        }

        @Test
        @DisplayName("different name — returns false")
        void existsByNameIgnoreCaseAndIdNot_DifferentName_ReturnsFalse() {
            assertThat(repository.existsByNameIgnoreCaseAndIdNot(
                    "Brand New Class", spinning.getId())).isFalse();
        }
    }
}
