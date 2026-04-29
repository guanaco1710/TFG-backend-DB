package com.example.tfgbackend.gym;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.gym.dto.GymRequest;
import com.example.tfgbackend.gym.dto.GymResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping(GymController.GYMS_BASE)
public class GymController {

    static final String GYMS_BASE = "/api/v1/gyms";

    private final GymService gymService;

    @GetMapping
    public ResponseEntity<PageResponse<GymResponse>> listGyms(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(gymService.listGyms(city, active, q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GymResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(gymService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GymResponse> create(@Valid @RequestBody GymRequest request) {
        GymResponse response = gymService.create(request);
        URI location = URI.create(GYMS_BASE + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GymResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody GymRequest request) {
        return ResponseEntity.ok(gymService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gymService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
