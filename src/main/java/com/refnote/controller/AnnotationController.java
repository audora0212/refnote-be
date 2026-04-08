package com.refnote.controller;

import com.refnote.dto.annotation.AnnotationBatchRequest;
import com.refnote.dto.annotation.AnnotationCreateRequest;
import com.refnote.dto.annotation.AnnotationResponse;
import com.refnote.dto.common.ApiResponse;
import com.refnote.service.AnnotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents/{documentId}/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<AnnotationResponse>>>> getAnnotations(
            @PathVariable Long documentId,
            @RequestParam(required = false) Integer pageNumber,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<AnnotationResponse> annotations = annotationService.getAnnotations(documentId, pageNumber, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("annotations", annotations)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnnotationResponse>> createAnnotation(
            @PathVariable Long documentId,
            @Valid @RequestBody AnnotationCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AnnotationResponse response = annotationService.createAnnotation(documentId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, List<AnnotationResponse>>>> createAnnotationBatch(
            @PathVariable Long documentId,
            @Valid @RequestBody AnnotationBatchRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<AnnotationResponse> responses = annotationService.createAnnotationBatch(documentId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(Map.of("annotations", responses)));
    }

    @DeleteMapping("/{annotationId}")
    public ResponseEntity<Void> deleteAnnotation(
            @PathVariable Long documentId,
            @PathVariable Long annotationId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        annotationService.deleteAnnotation(documentId, annotationId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllAnnotations(
            @PathVariable Long documentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        annotationService.deleteAllAnnotations(documentId, userId);
        return ResponseEntity.noContent().build();
    }
}
