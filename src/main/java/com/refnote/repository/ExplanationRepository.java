package com.refnote.repository;

import com.refnote.entity.Explanation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExplanationRepository extends JpaRepository<Explanation, Long> {
    List<Explanation> findByDocumentIdOrderByExplanationOrderAsc(Long documentId);
    long countByDocumentIdAndCreatedAtAfter(Long documentId, java.time.LocalDateTime after);
    long countByDocument_User_IdAndRegeneratedAtIsNotNullAndRegeneratedAtAfter(Long userId, java.time.LocalDateTime after);
}
