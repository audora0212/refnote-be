package com.refnote.service;

import com.refnote.dto.note.NoteCreateRequest;
import com.refnote.dto.note.NoteResponse;
import com.refnote.dto.note.NoteUpdateRequest;
import com.refnote.entity.*;
import com.refnote.exception.ApiException;
import com.refnote.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfBlockRepository pdfBlockRepository;
    private final ReviewQueueService reviewQueueService;

    @Transactional
    public NoteResponse createNote(Long documentId, NoteCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        Document document = findDocumentAndVerifyOwner(documentId, userId);

        PdfBlock block = null;
        if (request.getBlockId() != null) {
            block = pdfBlockRepository.findById(request.getBlockId())
                    .orElseThrow(() -> ApiException.notFound("블록을 찾을 수 없습니다."));
            if (!block.getDocument().getId().equals(documentId)) {
                throw ApiException.badRequest("해당 문서에 속하지 않는 블록입니다.");
            }
        }

        Note note = Note.builder()
                .user(user)
                .document(document)
                .block(block)
                .content(request.getContent())
                .build();

        note = noteRepository.save(note);

        if (block != null) {
            reviewQueueService.onNoteCreated(note);
        }

        log.info("노트 생성 - noteId: {}, documentId: {}, blockId: {}",
                note.getId(), documentId, request.getBlockId());
        return NoteResponse.from(note);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> getNotes(Long documentId, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        return noteRepository.findAllByUserIdAndDocumentIdOrderByCreatedAtDesc(userId, documentId)
                .stream()
                .map(NoteResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public NoteResponse updateNote(Long documentId, Long noteId, NoteUpdateRequest request, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        Note note = findNoteAndVerifyOwner(noteId, userId);

        if (!note.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서에 속하지 않는 노트입니다.");
        }

        note.setContent(request.getContent());
        note = noteRepository.save(note);

        log.info("노트 수정 - noteId: {}", noteId);
        return NoteResponse.from(note);
    }

    @Transactional
    public void deleteNote(Long documentId, Long noteId, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        Note note = findNoteAndVerifyOwner(noteId, userId);

        if (!note.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서에 속하지 않는 노트입니다.");
        }

        noteRepository.delete(note);
        log.info("노트 삭제 - noteId: {}, documentId: {}", noteId, documentId);
    }

    private Document findDocumentAndVerifyOwner(Long documentId, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("문서를 찾을 수 없습니다."));

        if (!document.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 문서에 대한 접근 권한이 없습니다.");
        }

        return document;
    }

    private Note findNoteAndVerifyOwner(Long noteId, Long userId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> ApiException.notFound("노트를 찾을 수 없습니다."));

        if (!note.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 노트에 대한 접근 권한이 없습니다.");
        }

        return note;
    }
}
