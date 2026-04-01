package com.refnote.service;

import com.refnote.dto.document.*;
import com.refnote.dto.explanation.ExplanationResponse;
import com.refnote.entity.Document;
import com.refnote.entity.User;
import com.refnote.exception.ApiException;
import com.refnote.repository.DocumentRepository;
import com.refnote.repository.PdfBlockRepository;
import com.refnote.repository.ExplanationRepository;
import com.refnote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfBlockRepository pdfBlockRepository;
    private final ExplanationRepository explanationRepository;
    private final FileStorageService fileStorageService;
    private final PdfParsingService pdfParsingService;

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, String title, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        if (title == null || title.isBlank()) {
            title = file.getOriginalFilename() != null
                    ? file.getOriginalFilename().replace(".pdf", "")
                    : "제목 없음";
        }

        Document document = Document.builder()
                .user(user)
                .title(title)
                .status(Document.DocumentStatus.UPLOADING)
                .build();
        document = documentRepository.save(document);

        String s3Key = fileStorageService.upload(file, userId, document.getId());
        document.setS3Key(s3Key);
        document.setS3Url(fileStorageService.getFileUrl(s3Key));
        documentRepository.save(document);

        // 트랜잭션 커밋 후 비동기 파싱 시작 (커밋 전에 @Async 호출하면 DB에서 문서를 못 찾음)
        Long docId = document.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pdfParsingService.parseAndGenerate(docId);
            }
        });

        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentListResponse getDocuments(Long userId) {
        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<DocumentResponse> responses = documents.stream()
                .map(DocumentResponse::from)
                .collect(Collectors.toList());
        return DocumentListResponse.builder().documents(responses).build();
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocument(Long documentId, Long userId) {
        Document document = findDocumentAndVerifyOwner(documentId, userId);

        String presignedUrl = document.getS3Key() != null
                ? fileStorageService.getFileUrl(document.getS3Key())
                : null;

        List<PdfBlockResponse> blocks = pdfBlockRepository
                .findByDocumentIdOrderByPageNumberAscBlockOrderAsc(documentId)
                .stream()
                .map(PdfBlockResponse::from)
                .collect(Collectors.toList());

        List<ExplanationResponse> explanations = explanationRepository
                .findByDocumentIdOrderByExplanationOrderAsc(documentId)
                .stream()
                .map(ExplanationResponse::from)
                .collect(Collectors.toList());

        return DocumentDetailResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .s3Url(presignedUrl)
                .pageCount(document.getPageCount())
                .status(document.getStatus().name())
                .blocks(blocks)
                .explanations(explanations)
                .createdAt(document.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteDocument(Long documentId, Long userId) {
        Document document = findDocumentAndVerifyOwner(documentId, userId);

        if (document.getS3Key() != null) {
            fileStorageService.delete(document.getS3Key());
        }

        documentRepository.delete(document);
        log.info("문서 삭제 완료 - documentId: {}, userId: {}", documentId, userId);
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getDocumentStatus(Long documentId, Long userId) {
        Document document = findDocumentAndVerifyOwner(documentId, userId);

        int progress = switch (document.getStatus()) {
            case UPLOADING -> 10;
            case PARSING -> 40;
            case GENERATING -> 70;
            case READY -> 100;
            case FAILED -> 0;
        };

        return DocumentStatusResponse.builder()
                .status(document.getStatus().name())
                .progress(progress)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PdfBlockResponse> getBlocks(Long documentId, Integer page, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        if (page != null) {
            return pdfBlockRepository.findByDocumentIdAndPageNumberOrderByBlockOrderAsc(documentId, page)
                    .stream()
                    .map(PdfBlockResponse::from)
                    .collect(Collectors.toList());
        }

        return pdfBlockRepository.findByDocumentIdOrderByPageNumberAscBlockOrderAsc(documentId)
                .stream()
                .map(PdfBlockResponse::from)
                .collect(Collectors.toList());
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
