package com.refnote.controller;

import com.refnote.dto.common.ApiResponse;
import com.refnote.dto.subject.SubjectCreateRequest;
import com.refnote.dto.subject.SubjectResponse;
import com.refnote.dto.subject.SubjectUpdateRequest;
import com.refnote.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubjectResponse>> createSubject(
            @Valid @RequestBody SubjectCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SubjectResponse response = subjectService.createSubject(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<SubjectResponse>>>> getSubjects(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<SubjectResponse> subjects = subjectService.getSubjects(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("subjects", subjects)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SubjectResponse>> updateSubject(
            @PathVariable Long id,
            @Valid @RequestBody SubjectUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SubjectResponse response = subjectService.updateSubject(id, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        subjectService.deleteSubject(id, userId);
        return ResponseEntity.noContent().build();
    }
}
