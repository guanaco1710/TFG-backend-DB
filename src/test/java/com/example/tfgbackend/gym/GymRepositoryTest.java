package com.example.tfgbackend.gym;

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
 * Repository slice test for {@link GymRepository}.
 *
 * Uses Testcontainers PostgreSQL (NOT H2) as the JPQL query uses LOWER() and
 * LIKE with CONCAT() that behave correctly only on a real PostgreSQL instance.
 */
class GymRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    GymRepository gymRepository;

    private Gym gymMadridActive;
    private Gym gymBarcelonaActive;
    private Gym gymMadridInactive;

    @BeforeEach
    void setUp() {
        gymMadridActive = em.persistAndFlush(Gym.builder()
                .name("FitZone Madrid")
                .address("Calle Mayor 1")
                .city("Madrid")
                .phone("+34 911 000 001")
                .openingHours("07:00-22:00")
                .active(true)
                .build());

        gymBarcelonaActive = em.persistAndFlush(Gym.builder()
                .name("CrossFit Barcelona")
                .address("Passeig de Gracia 200")
                .city("Barcelona")
                .phone(null)
                .openingHours("06:00-21:00")
                .active(true)
                .build());

        gymMadridInactive = em.persistAndFlush(Gym.builder()
                .name("OldGym Madrid")
                .address("Calle Vieja 10")
                .city("Madrid")
                .phone(null)
                .openingHours(null)
                .active(false)
                .build());

        em.clear();
    }

    // ---------------------------------------------------------------------------
    // existsByNameIgnoreCase
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("existsByNameIgnoreCase")
    class ExistsByNameIgnoreCase {

        @Test
        @DisplayName("exact match — returns true")
        void existsByNameIgnoreCase_ExactMatch_ReturnsTrue() {
            assertThat(gymRepository.existsByNameIgnoreCase("FitZone Madrid")).isTrue();
        }

        @Test
        @DisplayName("different case — returns true")
        void existsByNameIgnoreCase_DifferentCase_ReturnsTrue() {
            assertThat(gymRepository.existsByNameIgnoreCase("fitzone madrid")).isTrue();
            assertThat(gymRepository.existsByNameIgnoreCase("FITZONE MADRID")).isTrue();
        }

        @Test
        @DisplayName("unknown name — returns false")
        void existsByNameIgnoreCase_UnknownName_ReturnsFalse() {
            assertThat(gymRepository.existsByNameIgnoreCase("NonExistent Gym")).isFalse();
        }
    }

    // ---------------------------------------------------------------------------
    // existsByNameIgnoreCaseAndIdNot
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("existsByNameIgnoreCaseAndIdNot")
    class ExistsByNameIgnoreCaseAndIdNot {

        @Test
        @DisplayName("another gym has same name — returns true")
        void existsByNameIgnoreCaseAndIdNot_AnotherGymHasSameName_ReturnsTrue() {
            // gymBarcelonaActive has name "CrossFit Barcelona"; checking from gymMadridActive's id
            assertThat(gymRepository.existsByNameIgnoreCaseAndIdNot(
                    "CrossFit Barcelona", gymMadridActive.getId())).isTrue();
        }

        @Test
        @DisplayName("same gym name and same id — returns false")
        void existsByNameIgnoreCaseAndIdNot_SameGymSameId_ReturnsFalse() {
            // Checking whether the same gym conflicts with itself — it should not
            assertThat(gymRepository.existsByNameIgnoreCaseAndIdNot(
                    "FitZone Madrid", gymMadridActive.getId())).isFalse();
        }

        @Test
        @DisplayName("unique name — returns false")
        void existsByNameIgnoreCaseAndIdNot_UniqueName_ReturnsFalse() {
            assertThat(gymRepository.existsByNameIgnoreCaseAndIdNot(
                    "Brand New Gym", gymMadridActive.getId())).isFalse();
        }

        @Test
        @DisplayName("case-insensitive match against another gym — returns true")
        void existsByNameIgnoreCaseAndIdNot_CaseInsensitiveMatchAnotherGym_ReturnsTrue() {
            assertThat(gymRepository.existsByNameIgnoreCaseAndIdNot(
                    "crossfit barcelona", gymMadridActive.getId())).isTrue();
        }
    }

    // ---------------------------------------------------------------------------
    // findByFilters
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("findByFilters")
    class FindByFilters {

        @Test
        @DisplayName("no filters — returns all gyms")
        void findByFilters_NoFilters_ReturnsAll() {
            Page<Gym> result = gymRepository.findByFilters(null, null, null, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("city filter 'Madrid' — returns only Madrid gyms")
        void findByFilters_CityMadrid_ReturnsMadridGymsOnly() {
            Page<Gym> result = gymRepository.findByFilters("Madrid", null, null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .hasSize(2)
                    .allMatch(g -> g.getCity().equalsIgnoreCase("Madrid"));
        }

        @Test
        @DisplayName("city filter is case-insensitive — 'madrid' matches 'Madrid'")
        void findByFilters_CityLowercase_MatchesCaseInsensitive() {
            Page<Gym> result = gymRepository.findByFilters("madrid", null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("active=true filter — returns only active gyms")
        void findByFilters_ActiveTrue_ReturnsOnlyActiveGyms() {
            Page<Gym> result = gymRepository.findByFilters(null, true, null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .hasSize(2)
                    .allMatch(Gym::isActive);
        }

        @Test
        @DisplayName("active=false filter — returns only inactive gyms")
        void findByFilters_ActiveFalse_ReturnsOnlyInactiveGyms() {
            Page<Gym> result = gymRepository.findByFilters(null, false, null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .hasSize(1)
                    .noneMatch(Gym::isActive);
        }

        @Test
        @DisplayName("q filter matches gym name — returns matching gym")
        void findByFilters_QMatchesName_ReturnsMatchingGym() {
            Page<Gym> result = gymRepository.findByFilters(null, null, "FitZone", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("FitZone Madrid");
        }

        @Test
        @DisplayName("q filter matches address — returns matching gym")
        void findByFilters_QMatchesAddress_ReturnsMatchingGym() {
            Page<Gym> result = gymRepository.findByFilters(null, null, "Passeig", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("CrossFit Barcelona");
        }

        @Test
        @DisplayName("q filter is case-insensitive — lowercase matches mixed-case name")
        void findByFilters_QCaseInsensitive_Matches() {
            Page<Gym> result = gymRepository.findByFilters(null, null, "fitzone", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("FitZone Madrid");
        }

        @Test
        @DisplayName("q filter with no match — returns empty page")
        void findByFilters_QNoMatch_ReturnsEmpty() {
            Page<Gym> result = gymRepository.findByFilters(null, null, "ZZZNonExistent", PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("combined city and active filters — returns matching gyms")
        void findByFilters_CityAndActiveFilters_ReturnsCombinedResult() {
            Page<Gym> result = gymRepository.findByFilters("Madrid", true, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("FitZone Madrid");
            assertThat(result.getContent().get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("combined city, active and q filters — returns only matching gym")
        void findByFilters_AllFilters_ReturnsOnlyMatch() {
            Page<Gym> result = gymRepository.findByFilters("Barcelona", true, "CrossFit", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("CrossFit Barcelona");
        }

        @Test
        @DisplayName("pagination is respected — page 0 size 1 returns 1 element out of 3")
        void findByFilters_Paginated_ReturnsCorrectPage() {
            Page<Gym> result = gymRepository.findByFilters(null, null, null, PageRequest.of(0, 1));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.hasNext()).isTrue();
        }
    }
}
