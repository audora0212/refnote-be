package com.refnote.repository;

import com.refnote.entity.PdfBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdfBlockRepository extends JpaRepository<PdfBlock, Long> {
    List<PdfBlock> findByDocumentIdOrderByPageNumberAscBlockOrderAsc(Long documentId);
    List<PdfBlock> findByDocumentIdAndPageNumberOrderByBlockOrderAsc(Long documentId, Integer pageNumber);
}
