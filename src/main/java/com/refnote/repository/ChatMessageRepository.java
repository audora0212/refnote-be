package com.refnote.repository;

import com.refnote.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByDocumentIdAndUserIdOrderByCreatedAtAsc(Long documentId, Long userId);
    long countByUserIdAndRoleAndCreatedAtAfter(Long userId, ChatMessage.MessageRole role, java.time.LocalDateTime after);
    long countByDocumentIdAndUserIdAndRole(Long documentId, Long userId, ChatMessage.MessageRole role);
}
