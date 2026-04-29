package com.example.tfgbackend.gym;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.GymNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.gym.dto.GymRequest;
import com.example.tfgbackend.gym.dto.GymResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymService {

    private final GymRepository gymRepository;

    public PageResponse<GymResponse> listGyms(String city, Boolean active, String q, Pageable pageable) {
        // Treat blank strings as null so the JPQL filter skips them
        String cityFilter = (city != null && !city.isBlank()) ? city : null;
        String qFilter = (q != null && !q.isBlank()) ? q : null;
        return PageResponse.of(gymRepository.findByFilters(cityFilter, active, qFilter, pageable)
                .map(this::toResponse));
    }

    public GymResponse getById(Long id) {
        return toResponse(gymRepository.findById(id)
                .orElseThrow(() -> new GymNotFoundException(id)));
    }

    @Transactional
    public GymResponse create(GymRequest request) {
        if (gymRepository.existsByNameIgnoreCase(request.name())) {
            throw new GymNameAlreadyExistsException(request.name());
        }
        Gym gym = Gym.builder()
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .phone(normalisePhone(request.phone()))
                .openingHours(request.openingHours())
                .active(true)
                .build();
        return toResponse(gymRepository.save(gym));
    }

    @Transactional
    public GymResponse update(Long id, GymRequest request) {
        Gym gym = gymRepository.findById(id)
                .orElseThrow(() -> new GymNotFoundException(id));

        if (gymRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new GymNameAlreadyExistsException(request.name());
        }

        gym.setName(request.name());
        gym.setAddress(request.address());
        gym.setCity(request.city());
        gym.setPhone(normalisePhone(request.phone()));
        gym.setOpeningHours(request.openingHours());

        return toResponse(gymRepository.save(gym));
    }

    // Soft delete: marks the gym inactive instead of removing the DB row
    @Transactional
    public void delete(Long id) {
        Gym gym = gymRepository.findById(id)
                .orElseThrow(() -> new GymNotFoundException(id));
        gym.setActive(false);
        gymRepository.save(gym);
    }

    private GymResponse toResponse(Gym gym) {
        return new GymResponse(
                gym.getId(),
                gym.getName(),
                gym.getAddress(),
                gym.getCity(),
                gym.getPhone(),
                gym.getOpeningHours(),
                gym.isActive(),
                gym.getCreatedAt(),
                gym.getUpdatedAt()
        );
    }

    /** Converts a blank or whitespace-only phone string to null before persisting. */
    private String normalisePhone(String phone) {
        return (phone != null && !phone.isBlank()) ? phone : null;
    }
}
