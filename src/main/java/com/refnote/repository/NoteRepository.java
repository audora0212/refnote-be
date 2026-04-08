package com.refnote.repository;

import com.refnote.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findAllByUserIdAndDocumentIdOrderByCreatedAtDesc(Long userId, Long documentId);

    List<Note> findAllByBlockId(Long blockId);

    int countByDocumentId(Long documentId);

    void deleteAllByDocumentId(Long documentId);
}
