package com.refnote.service;

import com.refnote.dto.studytag.*;
import com.refnote.entity.*;
import com.refnote.exception.ApiException;
import com.refnote.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyTagService {

    private final StudyTagRepository studyTagRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfBlockRepository pdfBlockRepository;
    private final ReviewQueueService reviewQueueService;

    @Transactional
    public TagResponse createTag(Long documentId, TagCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        Document document = findDocumentAndVerifyOwner(documentId, userId);

        PdfBlock block = pdfBlockRepository.findById(request.getBlockId())
                .orElseThrow(() -> ApiException.notFound("블록을 찾을 수 없습니다."));

        if (!block.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서에 속하지 않는 블록입니다.");
        }

        if (studyTagRepository.existsByUserIdAndBlockIdAndTagType(userId, request.getBlockId(), request.getTagType())) {
            throw ApiException.conflict("이미 동일한 태그가 존재합니다.");
        }

        StudyTag tag = StudyTag.builder()
                .user(user)
                .document(document)
                .block(block)
                .tagType(request.getTagType())
                .build();

        tag = studyTagRepository.save(tag);
        reviewQueueService.onTagCreated(tag);

        log.info("태그 생성 - tagId: {}, documentId: {}, blockId: {}, type: {}",
                tag.getId(), documentId, request.getBlockId(), request.getTagType());
        return TagResponse.from(tag);
    }

    @Transactional(readOnly = true)
    public TagListResponse getTags(Long documentId, TagType tagType, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        List<StudyTag> tags;
        if (tagType != null) {
            tags = studyTagRepository.findAllByUserIdAndDocumentIdAndTagType(userId, documentId, tagType);
        } else {
            tags = studyTagRepository.findAllByUserIdAndDocumentId(userId, documentId);
        }

        List<TagResponse> tagResponses = tags.stream()
                .map(TagResponse::from)
                .collect(Collectors.toList());

        Map<String, Long> summary = buildSummary(userId, documentId);

        return TagListResponse.builder()
                .tags(tagResponses)
                .summary(summary)
                .build();
    }

    @Transactional
    public TagResponse updateTag(Long documentId, Long tagId, TagUpdateRequest request, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        StudyTag tag = studyTagRepository.findById(tagId)
                .orElseThrow(() -> ApiException.notFound("태그를 찾을 수 없습니다."));

        if (!tag.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 태그에 대한 접근 권한이 없습니다.");
        }

        if (!tag.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서에 속하지 않는 태그입니다.");
        }

        if (studyTagRepository.existsByUserIdAndBlockIdAndTagType(userId, tag.getBlock().getId(), request.getTagType())) {
            throw ApiException.conflict("이미 동일한 태그 유형이 존재합니다.");
        }

        tag.setTagType(request.getTagType());
        tag = studyTagRepository.save(tag);

        log.info("태그 수정 - tagId: {}, newType: {}", tagId, request.getTagType());
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteTag(Long documentId, Long tagId, Long userId) {
        findDocumentAndVerifyOwner(documentId, userId);

        StudyTag tag = studyTagRepository.findById(tagId)
                .orElseThrow(() -> ApiException.notFound("태그를 찾을 수 없습니다."));

        if (!tag.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 태그에 대한 접근 권한이 없습니다.");
        }

        if (!tag.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서에 속하지 않는 태그입니다.");
        }

        studyTagRepository.delete(tag);
        log.info("태그 삭제 - tagId: {}, documentId: {}", tagId, documentId);
    }

    private Map<String, Long> buildSummary(Long userId, Long documentId) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (TagType type : TagType.values()) {
            summary.put(type.name(), 0L);
        }

        List<Object[]> counts = studyTagRepository.countByTagType(userId, documentId);
        for (Object[] row : counts) {
            summary.put(((TagType) row[0]).name(), (Long) row[1]);
        }

        return summary;
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
