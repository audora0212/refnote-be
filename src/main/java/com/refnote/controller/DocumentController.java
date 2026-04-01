package com.refnote.controller;

import com.refnote.dto.document.*;
import com.refnote.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        DocumentResponse response = documentService.uploadDocument(file, title, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "data", response
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDocuments(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        DocumentListResponse response = documentService.getDocuments(userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        DocumentDetailResponse response = documentService.getDocument(id, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        documentService.deleteDocument(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        DocumentStatusResponse response = documentService.getDocumentStatus(id, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @GetMapping("/{id}/blocks")
    public ResponseEntity<Map<String, Object>> getBlocks(
            @PathVariable Long id,
            @RequestParam(value = "page", required = false) Integer page,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<PdfBlockResponse> blocks = documentService.getBlocks(id, page, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("blocks", blocks)
        ));
    }
}
