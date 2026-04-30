package com.example.tfgbackend.classtype;

import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.ClassTypeInUseException;
import com.example.tfgbackend.common.exception.ClassTypeNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.classtype.dto.ClassTypeRequest;
import com.example.tfgbackend.classtype.dto.ClassTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassTypeService {

    private final ClassTypeRepository classTypeRepository;
    private final ClassSessionRepository classSessionRepository;

    public PageResponse<ClassTypeResponse> list(String level, String q, Pageable pageable) {
        String levelFilter = (level != null && !level.isBlank()) ? level : null;
        String qFilter = (q != null && !q.isBlank()) ? q : null;
        return PageResponse.of(classTypeRepository.findByFilters(levelFilter, qFilter, pageable)
                .map(this::toResponse));
    }

    public ClassTypeResponse getById(Long id) {
        return toResponse(classTypeRepository.findById(id)
                .orElseThrow(() -> new ClassTypeNotFoundException(id)));
    }

    @Transactional
    public ClassTypeResponse create(ClassTypeRequest request) {
        if (classTypeRepository.existsByNameIgnoreCase(request.name())) {
            throw new ClassTypeNameAlreadyExistsException(request.name());
        }
        ClassType classType = ClassType.builder()
                .name(request.name())
                .description(request.description())
                .level(request.level())
                .build();
        return toResponse(classTypeRepository.save(classType));
    }

    @Transactional
    public ClassTypeResponse update(Long id, ClassTypeRequest request) {
        ClassType classType = classTypeRepository.findById(id)
                .orElseThrow(() -> new ClassTypeNotFoundException(id));
        if (classTypeRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ClassTypeNameAlreadyExistsException(request.name());
        }
        classType.setName(request.name());
        classType.setDescription(request.description());
        classType.setLevel(request.level());
        return toResponse(classTypeRepository.save(classType));
    }

    @Transactional
    public void delete(Long id) {
        ClassType classType = classTypeRepository.findById(id)
                .orElseThrow(() -> new ClassTypeNotFoundException(id));
        if (classSessionRepository.existsByClassTypeId(id)) {
            throw new ClassTypeInUseException(id);
        }
        classTypeRepository.delete(classType);
    }

    private ClassTypeResponse toResponse(ClassType classType) {
        return new ClassTypeResponse(
                classType.getId(),
                classType.getName(),
                classType.getDescription(),
                classType.getLevel()
        );
    }
}
