package com.refnote.repository;

import com.refnote.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Document> findByUserIdAndSubjectIdOrderByCreatedAtDesc(Long userId, Long subjectId);
    long countByUserId(Long userId);
    int countBySubjectId(Long subjectId);
}
