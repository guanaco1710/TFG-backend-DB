package com.example.tfgbackend.classtype;

import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.ClassTypeInUseException;
import com.example.tfgbackend.common.exception.ClassTypeNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.classtype.dto.ClassTypeRequest;
import com.example.tfgbackend.classtype.dto.ClassTypeResponse;
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

@ExtendWith(MockitoExtension.class)
class ClassTypeServiceTest {

    @Mock
    ClassTypeRepository classTypeRepository;

    @Mock
    ClassSessionRepository classSessionRepository;

    @InjectMocks
    ClassTypeService classTypeService;

    private ClassType spinning;
    private ClassType yoga;

    @BeforeEach
    void setUp() {
        spinning = ClassType.builder()
                .name("Spinning 45min")
                .description("High-intensity cycling")
                .level("INTERMEDIATE")
                .build();
        setId(spinning, 1L);

        yoga = ClassType.builder()
                .name("Yoga Flow")
                .description(null)
                .level("BASIC")
                .build();
        setId(yoga, 2L);
    }

    // ---------------------------------------------------------------------------
    // list
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("list")
    class List {

        @Test
        @DisplayName("both filters null — passes nulls to repository and returns page")
        void list_BothFiltersNull_PassesNullsToRepository() {
            Page<ClassType> page = new PageImpl<>(java.util.List.of(spinning, yoga), PageRequest.of(0, 10), 2);
            when(classTypeRepository.findByFilters(isNull(), isNull(), any(Pageable.class))).thenReturn(page);

            PageResponse<ClassTypeResponse> result = classTypeService.list(null, null, PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
            verify(classTypeRepository).findByFilters(null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("blank filters — normalised to null before forwarding")
        void list_BlankFilters_NormalisedToNull() {
            Page<ClassType> page = new PageImpl<>(java.util.List.of(spinning), PageRequest.of(0, 10), 1);
            when(classTypeRepository.findByFilters(isNull(), isNull(), any(Pageable.class))).thenReturn(page);

            classTypeService.list("  ", "  ", PageRequest.of(0, 10));

            verify(classTypeRepository).findByFilters(null, null, PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("both filters provided — forwarded as-is to repository")
        void list_BothFiltersProvided_ForwardedToRepository() {
            Page<ClassType> page = new PageImpl<>(java.util.List.of(spinning), PageRequest.of(0, 10), 1);
            when(classTypeRepository.findByFilters(eq("INTERMEDIATE"), eq("Spin"), any(Pageable.class))).thenReturn(page);

            PageResponse<ClassTypeResponse> result = classTypeService.list("INTERMEDIATE", "Spin", PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            verify(classTypeRepository).findByFilters("INTERMEDIATE", "Spin", PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("response fields are correctly mapped from entity")
        void list_ResponseFieldsMappedFromEntity() {
            Page<ClassType> page = new PageImpl<>(java.util.List.of(spinning), PageRequest.of(0, 10), 1);
            when(classTypeRepository.findByFilters(any(), any(), any())).thenReturn(page);

            PageResponse<ClassTypeResponse> result = classTypeService.list(null, null, PageRequest.of(0, 10));

            ClassTypeResponse resp = result.content().get(0);
            assertThat(resp.id()).isEqualTo(1L);
            assertThat(resp.name()).isEqualTo("Spinning 45min");
            assertThat(resp.description()).isEqualTo("High-intensity cycling");
            assertThat(resp.level()).isEqualTo("INTERMEDIATE");
        }
    }

    // ---------------------------------------------------------------------------
    // getById
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("class type exists — returns ClassTypeResponse")
        void getById_ClassTypeExists_ReturnsResponse() {
            when(classTypeRepository.findById(1L)).thenReturn(Optional.of(spinning));

            ClassTypeResponse result = classTypeService.getById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Spinning 45min");
            assertThat(result.level()).isEqualTo("INTERMEDIATE");
        }

        @Test
        @DisplayName("class type not found — throws ClassTypeNotFoundException")
        void getById_ClassTypeNotFound_ThrowsException() {
            when(classTypeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classTypeService.getById(999L))
                    .isInstanceOf(ClassTypeNotFoundException.class)
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
        @DisplayName("valid request — saves class type and returns response")
        void create_ValidRequest_SavesAndReturnsResponse() {
            ClassTypeRequest request = new ClassTypeRequest("Crossfit", "Functional training", "ADVANCED");
            when(classTypeRepository.existsByNameIgnoreCase("Crossfit")).thenReturn(false);
            when(classTypeRepository.save(any(ClassType.class))).thenAnswer(inv -> {
                ClassType saved = inv.getArgument(0);
                setId(saved, 10L);
                return saved;
            });

            ClassTypeResponse result = classTypeService.create(request);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.name()).isEqualTo("Crossfit");
            assertThat(result.description()).isEqualTo("Functional training");
            assertThat(result.level()).isEqualTo("ADVANCED");

            ArgumentCaptor<ClassType> captor = ArgumentCaptor.forClass(ClassType.class);
            verify(classTypeRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Crossfit");
            assertThat(captor.getValue().getLevel()).isEqualTo("ADVANCED");
        }

        @Test
        @DisplayName("duplicate name — throws ClassTypeNameAlreadyExistsException without saving")
        void create_DuplicateName_ThrowsExceptionWithoutSaving() {
            ClassTypeRequest request = new ClassTypeRequest("Spinning 45min", null, "BASIC");
            when(classTypeRepository.existsByNameIgnoreCase("Spinning 45min")).thenReturn(true);

            assertThatThrownBy(() -> classTypeService.create(request))
                    .isInstanceOf(ClassTypeNameAlreadyExistsException.class)
                    .hasMessageContaining("Spinning 45min");

            verify(classTypeRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // update
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("valid request — updates all fields and returns response")
        void update_ValidRequest_UpdatesAllFieldsAndReturnsResponse() {
            ClassTypeRequest request = new ClassTypeRequest("Spinning 60min", "Extended session", "ADVANCED");
            when(classTypeRepository.findById(1L)).thenReturn(Optional.of(spinning));
            when(classTypeRepository.existsByNameIgnoreCaseAndIdNot("Spinning 60min", 1L)).thenReturn(false);
            when(classTypeRepository.save(any(ClassType.class))).thenAnswer(inv -> inv.getArgument(0));

            ClassTypeResponse result = classTypeService.update(1L, request);

            assertThat(result.name()).isEqualTo("Spinning 60min");
            assertThat(result.description()).isEqualTo("Extended session");
            assertThat(result.level()).isEqualTo("ADVANCED");

            ArgumentCaptor<ClassType> captor = ArgumentCaptor.forClass(ClassType.class);
            verify(classTypeRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Spinning 60min");
        }

        @Test
        @DisplayName("class type not found — throws ClassTypeNotFoundException without saving")
        void update_ClassTypeNotFound_ThrowsException() {
            ClassTypeRequest request = new ClassTypeRequest("Anything", null, "BASIC");
            when(classTypeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classTypeService.update(999L, request))
                    .isInstanceOf(ClassTypeNotFoundException.class)
                    .hasMessageContaining("999");

            verify(classTypeRepository, never()).save(any());
        }

        @Test
        @DisplayName("name taken by another type — throws ClassTypeNameAlreadyExistsException without saving")
        void update_NameTakenByAnother_ThrowsExceptionWithoutSaving() {
            ClassTypeRequest request = new ClassTypeRequest("Yoga Flow", null, "BASIC");
            when(classTypeRepository.findById(1L)).thenReturn(Optional.of(spinning));
            when(classTypeRepository.existsByNameIgnoreCaseAndIdNot("Yoga Flow", 1L)).thenReturn(true);

            assertThatThrownBy(() -> classTypeService.update(1L, request))
                    .isInstanceOf(ClassTypeNameAlreadyExistsException.class)
                    .hasMessageContaining("Yoga Flow");

            verify(classTypeRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("no sessions reference this type — deletes successfully")
        void delete_NoSessionsReferencing_DeletesSuccessfully() {
            when(classTypeRepository.findById(1L)).thenReturn(Optional.of(spinning));
            when(classSessionRepository.existsByClassTypeId(1L)).thenReturn(false);

            classTypeService.delete(1L);

            verify(classTypeRepository).delete(spinning);
        }

        @Test
        @DisplayName("class type not found — throws ClassTypeNotFoundException without deleting")
        void delete_ClassTypeNotFound_ThrowsException() {
            when(classTypeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classTypeService.delete(999L))
                    .isInstanceOf(ClassTypeNotFoundException.class)
                    .hasMessageContaining("999");

            verify(classTypeRepository, never()).delete(any(ClassType.class));
        }

        @Test
        @DisplayName("sessions reference this type — throws ClassTypeInUseException without deleting")
        void delete_SessionsExist_ThrowsClassTypeInUseException() {
            when(classTypeRepository.findById(1L)).thenReturn(Optional.of(spinning));
            when(classSessionRepository.existsByClassTypeId(1L)).thenReturn(true);

            assertThatThrownBy(() -> classTypeService.delete(1L))
                    .isInstanceOf(ClassTypeInUseException.class)
                    .hasMessageContaining("1");

            verify(classTypeRepository, never()).delete(any(ClassType.class));
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
