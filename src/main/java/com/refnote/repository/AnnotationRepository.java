package com.refnote.repository;

import com.refnote.entity.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    List<Annotation> findAllByDocumentIdAndUserIdOrderByPageNumberAscCreatedAtAsc(Long documentId, Long userId);

    List<Annotation> findAllByDocumentIdAndUserIdAndPageNumber(Long documentId, Long userId, Integer pageNumber);

    void deleteAllByDocumentIdAndUserId(Long documentId, Long userId);
}
