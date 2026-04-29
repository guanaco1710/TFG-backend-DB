package com.example.tfgbackend.classsession;

import com.example.tfgbackend.classsession.dto.ClassSessionRequest;
import com.example.tfgbackend.classsession.dto.ClassSessionResponse;
import com.example.tfgbackend.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(ClassSessionController.BASE)
public class ClassSessionController {

    static final String BASE = "/api/v1/class-sessions";

    private final ClassSessionService classSessionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassSessionResponse> createSession(@Valid @RequestBody ClassSessionRequest request) {
        ClassSessionResponse response = classSessionService.createSession(request);
        URI location = URI.create(BASE + "/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassSessionResponse> getSessionById(@PathVariable Long id) {
        return ResponseEntity.ok(classSessionService.getSessionById(id));
    }

    @GetMapping("/schedule")
    public ResponseEntity<List<ClassSessionResponse>> getSchedule(
            @RequestParam @NotNull LocalDateTime from,
            @RequestParam @NotNull LocalDateTime to) {
        return ResponseEntity.ok(classSessionService.getSchedule(from, to));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClassSessionResponse>> listSessions(
            @RequestParam(required = false) Long classTypeId,
            Pageable pageable) {
        return ResponseEntity.ok(classSessionService.listSessions(classTypeId, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassSessionResponse> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody ClassSessionRequest request) {
        return ResponseEntity.ok(classSessionService.updateSession(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelSession(@PathVariable Long id) {
        classSessionService.cancelSession(id);
        return ResponseEntity.noContent().build();
    }
}
