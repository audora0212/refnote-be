package com.refnote.service;

import com.refnote.dto.annotation.AnnotationBatchRequest;
import com.refnote.dto.annotation.AnnotationCreateRequest;
import com.refnote.dto.annotation.AnnotationResponse;
import com.refnote.entity.Annotation;
import com.refnote.entity.Document;
import com.refnote.entity.User;
import com.refnote.exception.ApiException;
import com.refnote.repository.AnnotationRepository;
import com.refnote.repository.DocumentRepository;
import com.refnote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AnnotationResponse> getAnnotations(Long documentId, Integer pageNumber, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        List<Annotation> annotations;
        if (pageNumber != null) {
            annotations = annotationRepository.findAllByDocumentIdAndUserIdAndPageNumber(
                    documentId, userId, pageNumber);
        } else {
            annotations = annotationRepository.findAllByDocumentIdAndUserIdOrderByPageNumberAscCreatedAtAsc(
                    documentId, userId);
        }

        return annotations.stream()
                .map(AnnotationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AnnotationResponse createAnnotation(Long documentId, AnnotationCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        Document document = findDocumentAndVerifyOwner(documentId, userId);

        Annotation annotation = Annotation.builder()
                .user(user)
                .document(document)
                .pageNumber(request.getPageNumber())
                .type(request.getType())
                .data(request.getData())
                .color(request.getColor())
                .thickness(request.getThickness())
                .build();

        annotation = annotationRepository.save(annotation);

        log.info("주석 생성 - annotationId: {}, documentId: {}, page: {}, type: {}",
                annotation.getId(), documentId, request.getPageNumber(), request.getType());
        return AnnotationResponse.from(annotation);
    }

    @Transactional
    public List<AnnotationResponse> createAnnotationBatch(Long documentId, AnnotationBatchRequest batchRequest, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        Document document = findDocumentAndVerifyOwner(documentId, userId);

        List<Annotation> annotations = batchRequest.getAnnotations().stream()
                .map(request -> Annotation.builder()
                        .user(user)
                        .document(document)
                        .pageNumber(request.getPageNumber())
                        .type(request.getType())
                        .data(request.getData())
                        .color(request.getColor())
                        .thickness(request.getThickness())
                        .build())
                .collect(Collectors.toList());

        annotations = annotationRepository.saveAll(annotations);

        log.info("주석 일괄 생성 - documentId: {}, count: {}", documentId, annotations.size());
        return annotations.stream()
                .map(AnnotationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAnnotation(Long documentId, Long annotationId, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        Annotation annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> ApiException.notFound("주석을 찾을 수 없습니다."));

        if (!annotation.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 주석에 대한 접근 권한이 없습니다.");
        }

        if (!annotation.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서에 속하지 않는 주석입니다.");
        }

        annotationRepository.delete(annotation);
        log.info("주석 삭제 - annotationId: {}, documentId: {}", annotationId, documentId);
    }

    @Transactional
    public void deleteAllAnnotations(Long documentId, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        annotationRepository.deleteAllByDocumentIdAndUserId(documentId, userId);
        log.info("주석 전체 삭제 - documentId: {}, userId: {}", documentId, userId);
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
