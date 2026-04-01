package com.refnote.controller;

import com.refnote.dto.chat.ChatHistoryResponse;
import com.refnote.dto.chat.ChatRequest;
import com.refnote.dto.chat.ChatResponse;
import com.refnote.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/documents/{documentId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(
            @PathVariable Long documentId,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ChatResponse response = chatService.chat(documentId, request, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getChatHistory(
            @PathVariable Long documentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ChatHistoryResponse response = chatService.getChatHistory(documentId, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }
}
