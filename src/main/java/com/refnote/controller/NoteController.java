package com.refnote.controller;

import com.refnote.dto.common.ApiResponse;
import com.refnote.dto.note.NoteCreateRequest;
import com.refnote.dto.note.NoteResponse;
import com.refnote.dto.note.NoteUpdateRequest;
import com.refnote.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents/{docId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @PathVariable Long docId,
            @Valid @RequestBody NoteCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        NoteResponse response = noteService.createNote(docId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<NoteResponse>>>> getNotes(
            @PathVariable Long docId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<NoteResponse> notes = noteService.getNotes(docId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("notes", notes)));
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @PathVariable Long docId,
            @PathVariable Long noteId,
            @Valid @RequestBody NoteUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        NoteResponse response = noteService.updateNote(docId, noteId, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable Long docId,
            @PathVariable Long noteId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        noteService.deleteNote(docId, noteId, userId);
        return ResponseEntity.noContent().build();
    }
}
