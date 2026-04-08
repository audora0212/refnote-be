package com.refnote.service;

import com.refnote.dto.session.SessionResponse;
import com.refnote.dto.session.SessionUpdateRequest;
import com.refnote.entity.Document;
import com.refnote.entity.StudySession;
import com.refnote.entity.User;
import com.refnote.exception.ApiException;
import com.refnote.repository.DocumentRepository;
import com.refnote.repository.StudySessionRepository;
import com.refnote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SessionResponse getSession(Long documentId, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        StudySession session = studySessionRepository.findByUserIdAndDocumentId(userId, documentId)
                .orElse(null);

        if (session == null) {
            return SessionResponse.builder()
                    .documentId(documentId)
                    .currentPage(1)
                    .scrollPosition(0.0)
                    .lastActiveAt(null)
                    .build();
        }

        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse updateSession(Long documentId, SessionUpdateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        Document document = findDocumentAndVerifyOwner(documentId, userId);

        StudySession session = studySessionRepository.findByUserIdAndDocumentId(userId, documentId)
                .orElse(null);

        if (session == null) {
            session = StudySession.builder()
                    .user(user)
                    .document(document)
                    .currentPage(request.getCurrentPage())
                    .scrollPosition(request.getScrollPosition())
                    .build();
        } else {
            session.setCurrentPage(request.getCurrentPage());
            session.setScrollPosition(request.getScrollPosition());
        }

        session = studySessionRepository.save(session);
        log.debug("학습 위치 저장 - documentId: {}, page: {}, scroll: {}",
                documentId, request.getCurrentPage(), request.getScrollPosition());
        return SessionResponse.from(session);
    }

    private Document findDocumentAndVerifyOwner(Long documentId, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("문서를 찾을 수 없습니다."));

        if (!document.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 문서에 대한 접근 권한이 없습니다.");
        }

        return document;
    }
}
