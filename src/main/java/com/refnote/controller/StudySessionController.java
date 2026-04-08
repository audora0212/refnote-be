package com.refnote.controller;

import com.refnote.dto.common.ApiResponse;
import com.refnote.dto.session.SessionResponse;
import com.refnote.dto.session.SessionUpdateRequest;
import com.refnote.service.StudySessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{docId}/session")
@RequiredArgsConstructor
public class StudySessionController {

    private final StudySessionService studySessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<SessionResponse>> getSession(
            @PathVariable Long docId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SessionResponse response = studySessionService.getSession(docId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<SessionResponse>> updateSession(
            @PathVariable Long docId,
            @Valid @RequestBody SessionUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SessionResponse response = studySessionService.updateSession(docId, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
