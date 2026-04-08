package com.refnote.controller;

import com.refnote.dto.common.ApiResponse;
import com.refnote.dto.studytag.*;
import com.refnote.entity.TagType;
import com.refnote.service.StudyTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{docId}/tags")
@RequiredArgsConstructor
public class StudyTagController {

    private final StudyTagService studyTagService;

    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> createTag(
            @PathVariable Long docId,
            @Valid @RequestBody TagCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TagResponse response = studyTagService.createTag(docId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TagListResponse>> getTags(
            @PathVariable Long docId,
            @RequestParam(required = false) TagType tagType,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TagListResponse response = studyTagService.getTags(docId, tagType, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{tagId}")
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @PathVariable Long docId,
            @PathVariable Long tagId,
            @Valid @RequestBody TagUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TagResponse response = studyTagService.updateTag(docId, tagId, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable Long docId,
            @PathVariable Long tagId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        studyTagService.deleteTag(docId, tagId, userId);
        return ResponseEntity.noContent().build();
    }
}
