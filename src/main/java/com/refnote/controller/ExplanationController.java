package com.refnote.controller;

import com.refnote.dto.explanation.ExplanationListResponse;
import com.refnote.dto.explanation.ExplanationResponse;
import com.refnote.dto.explanation.RegenerateRequest;
import com.refnote.service.ExplanationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/documents/{documentId}/explanations")
@RequiredArgsConstructor
public class ExplanationController {

    private final ExplanationService explanationService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getExplanations(
            @PathVariable Long documentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ExplanationListResponse response = explanationService.getExplanations(documentId, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @PostMapping("/{explanationId}/regenerate")
    public ResponseEntity<Map<String, Object>> regenerate(
            @PathVariable Long documentId,
            @PathVariable Long explanationId,
            @Valid @RequestBody RegenerateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ExplanationResponse response = explanationService.regenerate(documentId, explanationId, request, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }
}
