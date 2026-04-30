package com.example.tfgbackend.classtype;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.classtype.dto.ClassTypeRequest;
import com.example.tfgbackend.classtype.dto.ClassTypeResponse;
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
@RequestMapping(ClassTypeController.BASE)
public class ClassTypeController {

    static final String BASE = "/api/v1/class-types";

    private final ClassTypeService classTypeService;

    @GetMapping
    public ResponseEntity<PageResponse<ClassTypeResponse>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(classTypeService.list(level, q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassTypeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(classTypeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassTypeResponse> create(@Valid @RequestBody ClassTypeRequest request) {
        ClassTypeResponse response = classTypeService.create(request);
        URI location = URI.create(BASE + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassTypeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ClassTypeRequest request) {
        return ResponseEntity.ok(classTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        classTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
