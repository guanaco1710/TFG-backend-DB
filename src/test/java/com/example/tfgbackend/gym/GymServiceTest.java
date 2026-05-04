package com.example.tfgbackend.gym;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.GymNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.gym.dto.GymRequest;
import com.example.tfgbackend.gym.dto.GymResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link GymService}. No Spring context — all collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class GymServiceTest {

    @Mock
    GymRepository gymRepository;

    @InjectMocks
    GymService gymService;

    private Gym gymA;
    private Gym gymB;

    @BeforeEach
    void setUp() {
        gymA = Gym.builder()
                .name("FitZone Madrid")
                .address("Calle Mayor 1")
                .city("Madrid")
                .phone("+34 911 000 001")
                .openingHours("07:00-22:00")
                .active(true)
                .build();
        setId(gymA, 1L);

        gymB = Gym.builder()
                .name("CrossFit Barcelona")
                .address("Passeig de Gracia 200")
                .city("Barcelona")
                .phone(null)
                .openingHours("06:00-21:00")
                .active(false)
                .build();
        setId(gymB, 2L);
    }

    // ---------------------------------------------------------------------------
    // listGyms
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("listGyms")
    class ListGyms {

        @Test
        @DisplayName("all filters null — passes null values to repository and returns page")
        void listGyms_AllFiltersNull_PassesNullsToRepositoryAndReturnsPage() {
            Page<Gym> page = new PageImpl<>(List.of(gymA, gymB), PageRequest.of(0, 10), 2);
            when(gymRepository.findByFilters(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms(null, null, null, null, PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
            verify(gymRepository).findByFilters(null, null, null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("city filter provided — forwarded as-is to repository")
        void listGyms_WithCityFilter_ForwardsCityToRepository() {
            Page<Gym> page = new PageImpl<>(List.of(gymA), PageRequest.of(0, 10), 1);
            when(gymRepository.findByFilters(eq("Madrid"), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms("Madrid", null, null, null, PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).city()).isEqualTo("Madrid");
            verify(gymRepository).findByFilters("Madrid", null, null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("blank city string — normalised to null before forwarding")
        void listGyms_BlankCity_NormalisedToNull() {
            Page<Gym> page = new PageImpl<>(List.of(gymA, gymB), PageRequest.of(0, 10), 2);
            when(gymRepository.findByFilters(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            gymService.listGyms("   ", null, null, null, PageRequest.of(0, 10));

            verify(gymRepository).findByFilters(null, null, null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("active filter provided — forwarded to repository")
        void listGyms_WithActiveFilter_ForwardsActiveToRepository() {
            Page<Gym> page = new PageImpl<>(List.of(gymA), PageRequest.of(0, 10), 1);
            when(gymRepository.findByFilters(isNull(), eq(true), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms(null, true, null, null, PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            verify(gymRepository).findByFilters(null, true, null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("name filter provided — forwarded to repository")
        void listGyms_WithNameFilter_ForwardsNameToRepository() {
            Page<Gym> page = new PageImpl<>(List.of(gymA), PageRequest.of(0, 10), 1);
            when(gymRepository.findByFilters(isNull(), isNull(), eq("FitZone"), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms(null, null, "FitZone", null, PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            verify(gymRepository).findByFilters(null, null, "FitZone", null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("blank name string — normalised to null before forwarding")
        void listGyms_BlankName_NormalisedToNull() {
            Page<Gym> page = new PageImpl<>(List.of(gymA, gymB), PageRequest.of(0, 10), 2);
            when(gymRepository.findByFilters(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            gymService.listGyms(null, null, "  ", null, PageRequest.of(0, 10));

            verify(gymRepository).findByFilters(null, null, null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("q filter provided — forwarded to repository")
        void listGyms_WithQFilter_ForwardsQToRepository() {
            Page<Gym> page = new PageImpl<>(List.of(gymA), PageRequest.of(0, 10), 1);
            when(gymRepository.findByFilters(isNull(), isNull(), isNull(), eq("FitZone"), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms(null, null, null, "FitZone", PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            verify(gymRepository).findByFilters(null, null, null, "FitZone", PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("blank q string — normalised to null before forwarding")
        void listGyms_BlankQ_NormalisedToNull() {
            Page<Gym> page = new PageImpl<>(List.of(gymA, gymB), PageRequest.of(0, 10), 2);
            when(gymRepository.findByFilters(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            gymService.listGyms(null, null, null, "  ", PageRequest.of(0, 10));

            verify(gymRepository).findByFilters(null, null, null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("all filters set — all forwarded to repository after normalisation")
        void listGyms_AllFiltersSet_AllForwardedToRepository() {
            Page<Gym> page = new PageImpl<>(List.of(gymA), PageRequest.of(0, 10), 1);
            when(gymRepository.findByFilters(eq("Madrid"), eq(true), eq("FitZone"), eq("Central"), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms("Madrid", true, "FitZone", "Central", PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            assertThat(result.page()).isZero();
            assertThat(result.hasMore()).isFalse();
            verify(gymRepository).findByFilters("Madrid", true, "FitZone", "Central", PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("response fields are correctly mapped from entity")
        void listGyms_ResponseFieldsMappedFromEntity() {
            Page<Gym> page = new PageImpl<>(List.of(gymA), PageRequest.of(0, 10), 1);
            when(gymRepository.findByFilters(any(), any(), any(), any(), any())).thenReturn(page);

            PageResponse<GymResponse> result = gymService.listGyms(null, null, null, null, PageRequest.of(0, 10));

            GymResponse resp = result.content().get(0);
            assertThat(resp.id()).isEqualTo(1L);
            assertThat(resp.name()).isEqualTo("FitZone Madrid");
            assertThat(resp.address()).isEqualTo("Calle Mayor 1");
            assertThat(resp.city()).isEqualTo("Madrid");
            assertThat(resp.phone()).isEqualTo("+34 911 000 001");
            assertThat(resp.openingHours()).isEqualTo("07:00-22:00");
            assertThat(resp.active()).isTrue();
        }
    }

    // ---------------------------------------------------------------------------
    // getById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("happy path — returns GymResponse for existing gym")
        void getById_GymExists_ReturnsGymResponse() {
            when(gymRepository.findById(1L)).thenReturn(Optional.of(gymA));

            GymResponse result = gymService.getById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("FitZone Madrid");
            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("gym not found — throws GymNotFoundException")
        void getById_GymNotFound_ThrowsGymNotFoundException() {
            when(gymRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gymService.getById(999L))
                    .isInstanceOf(GymNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ---------------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path — saves gym and returns response")
        void create_ValidRequest_SavesGymAndReturnsResponse() {
            GymRequest request = new GymRequest("NewGym", "Street 1", "Seville", "+34 954 000 001", "08:00-20:00");
            when(gymRepository.existsByNameIgnoreCase("NewGym")).thenReturn(false);
            when(gymRepository.save(any(Gym.class))).thenAnswer(inv -> {
                Gym saved = inv.getArgument(0);
                setId(saved, 10L);
                return saved;
            });

            GymResponse result = gymService.create(request);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.name()).isEqualTo("NewGym");
            assertThat(result.address()).isEqualTo("Street 1");
            assertThat(result.city()).isEqualTo("Seville");
            assertThat(result.phone()).isEqualTo("+34 954 000 001");
            assertThat(result.openingHours()).isEqualTo("08:00-20:00");
            assertThat(result.active()).isTrue();

            ArgumentCaptor<Gym> captor = ArgumentCaptor.forClass(Gym.class);
            verify(gymRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("null phone normalised — persisted as null")
        void create_NullPhone_PersistedAsNull() {
            GymRequest request = new GymRequest("PhonelessGym", "Street 2", "Valencia", null, null);
            when(gymRepository.existsByNameIgnoreCase("PhonelessGym")).thenReturn(false);
            when(gymRepository.save(any(Gym.class))).thenAnswer(inv -> {
                Gym saved = inv.getArgument(0);
                setId(saved, 11L);
                return saved;
            });

            GymResponse result = gymService.create(request);

            assertThat(result.phone()).isNull();
            ArgumentCaptor<Gym> captor = ArgumentCaptor.forClass(Gym.class);
            verify(gymRepository).save(captor.capture());
            assertThat(captor.getValue().getPhone()).isNull();
        }

        @Test
        @DisplayName("blank phone normalised — persisted as null")
        void create_BlankPhone_PersistedAsNull() {
            GymRequest request = new GymRequest("BlankPhoneGym", "Street 3", "Bilbao", "   ", null);
            when(gymRepository.existsByNameIgnoreCase("BlankPhoneGym")).thenReturn(false);
            when(gymRepository.save(any(Gym.class))).thenAnswer(inv -> {
                Gym saved = inv.getArgument(0);
                setId(saved, 12L);
                return saved;
            });

            gymService.create(request);

            ArgumentCaptor<Gym> captor = ArgumentCaptor.forClass(Gym.class);
            verify(gymRepository).save(captor.capture());
            assertThat(captor.getValue().getPhone()).isNull();
        }

        @Test
        @DisplayName("duplicate name — throws GymNameAlreadyExistsException")
        void create_DuplicateName_ThrowsGymNameAlreadyExistsException() {
            GymRequest request = new GymRequest("FitZone Madrid", "Another Street", "Madrid", null, null);
            when(gymRepository.existsByNameIgnoreCase("FitZone Madrid")).thenReturn(true);

            assertThatThrownBy(() -> gymService.create(request))
                    .isInstanceOf(GymNameAlreadyExistsException.class)
                    .hasMessageContaining("FitZone Madrid");

            verify(gymRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // update
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("happy path — updates all fields and returns updated response")
        void update_GymExistsAndNameFree_UpdatesAndReturnsResponse() {
            GymRequest request = new GymRequest("FitZone Madrid Updated", "New Street 1", "Madrid", "+34 911 999 999", "08:00-23:00");
            when(gymRepository.findById(1L)).thenReturn(Optional.of(gymA));
            when(gymRepository.existsByNameIgnoreCaseAndIdNot("FitZone Madrid Updated", 1L)).thenReturn(false);
            when(gymRepository.save(any(Gym.class))).thenAnswer(inv -> inv.getArgument(0));

            GymResponse result = gymService.update(1L, request);

            assertThat(result.name()).isEqualTo("FitZone Madrid Updated");
            assertThat(result.address()).isEqualTo("New Street 1");
            assertThat(result.city()).isEqualTo("Madrid");
            assertThat(result.phone()).isEqualTo("+34 911 999 999");
            assertThat(result.openingHours()).isEqualTo("08:00-23:00");

            ArgumentCaptor<Gym> captor = ArgumentCaptor.forClass(Gym.class);
            verify(gymRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("FitZone Madrid Updated");
        }

        @Test
        @DisplayName("update with null phone — normalises to null")
        void update_NullPhone_PersistedAsNull() {
            GymRequest request = new GymRequest("FitZone Madrid Updated", "New Street 1", "Madrid", null, null);
            when(gymRepository.findById(1L)).thenReturn(Optional.of(gymA));
            when(gymRepository.existsByNameIgnoreCaseAndIdNot(any(), eq(1L))).thenReturn(false);
            when(gymRepository.save(any(Gym.class))).thenAnswer(inv -> inv.getArgument(0));

            GymResponse result = gymService.update(1L, request);

            assertThat(result.phone()).isNull();
        }

        @Test
        @DisplayName("gym not found — throws GymNotFoundException")
        void update_GymNotFound_ThrowsGymNotFoundException() {
            GymRequest request = new GymRequest("AnyName", "Street", "City", null, null);
            when(gymRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gymService.update(999L, request))
                    .isInstanceOf(GymNotFoundException.class)
                    .hasMessageContaining("999");

            verify(gymRepository, never()).save(any());
        }

        @Test
        @DisplayName("name taken by another gym — throws GymNameAlreadyExistsException")
        void update_NameTakenByAnotherGym_ThrowsGymNameAlreadyExistsException() {
            GymRequest request = new GymRequest("CrossFit Barcelona", "New Street", "Madrid", null, null);
            when(gymRepository.findById(1L)).thenReturn(Optional.of(gymA));
            when(gymRepository.existsByNameIgnoreCaseAndIdNot("CrossFit Barcelona", 1L)).thenReturn(true);

            assertThatThrownBy(() -> gymService.update(1L, request))
                    .isInstanceOf(GymNameAlreadyExistsException.class)
                    .hasMessageContaining("CrossFit Barcelona");

            verify(gymRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // delete (soft delete)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("happy path — sets active=false on the gym")
        void delete_GymExists_SetsActiveFalse() {
            when(gymRepository.findById(1L)).thenReturn(Optional.of(gymA));
            when(gymRepository.save(any(Gym.class))).thenAnswer(inv -> inv.getArgument(0));

            gymService.delete(1L);

            ArgumentCaptor<Gym> captor = ArgumentCaptor.forClass(Gym.class);
            verify(gymRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("gym not found — throws GymNotFoundException")
        void delete_GymNotFound_ThrowsGymNotFoundException() {
            when(gymRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gymService.delete(999L))
                    .isInstanceOf(GymNotFoundException.class)
                    .hasMessageContaining("999");

            verify(gymRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void setId(Object entity, Long id) {
        try {
            var field = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
