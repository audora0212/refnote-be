package com.refnote.service;

import com.refnote.dto.explanation.ExplanationListResponse;
import com.refnote.dto.explanation.ExplanationResponse;
import com.refnote.dto.explanation.RegenerateRequest;
import com.refnote.entity.*;
import com.refnote.exception.ApiException;
import com.refnote.repository.DocumentRepository;
import com.refnote.repository.ExplanationRepository;
import com.refnote.repository.PdfBlockRepository;
import com.refnote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationService {

    private final ExplanationRepository explanationRepository;
    private final PdfBlockRepository pdfBlockRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final ClaudeApiService claudeApiService;

    private static final int FREE_DAILY_REGENERATE_LIMIT = 3;

    @Transactional
    public void generateExplanations(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다: " + documentId));

        List<PdfBlock> blocks = pdfBlockRepository.findByDocumentIdOrderByPageNumberAscBlockOrderAsc(documentId);

        if (blocks.isEmpty()) {
            document.setStatus(Document.DocumentStatus.FAILED);
            documentRepository.save(document);
            return;
        }

        List<List<PdfBlock>> groups = groupBlocks(blocks);

        int order = 0;
        for (List<PdfBlock> group : groups) {
            order++;
            String blockContent = group.stream()
                    .map(PdfBlock::getContent)
                    .collect(Collectors.joining("\n\n"));

            String blockIdsStr = group.stream()
                    .map(b -> b.getId().toString())
                    .collect(Collectors.joining(","));

            String systemPrompt = """
                    당신은 대학교 교수입니다. 학생에게 PDF 원문 내용을 강의하듯 설명해주세요.
                    다음 규칙을 반드시 따르세요:
                    1. 서론, 인사말, 맺음말, "이해하는 시간을 가지겠습니다" 같은 도입부 없이 바로 본론으로 시작하세요.
                    2. 핵심 개념을 명확하게 설명하세요.
                    3. 어려운 용어가 있으면 쉽게 풀어서 설명하세요.
                    4. 원문에 있는 내용만 기반으로 설명하세요. 외부 지식을 추가하지 마세요.
                    5. 교수 연락처, 이메일, 전화번호, 홈페이지, 강의실 정보, 수업 시간, 성적 비율 등 행정/메타데이터는 완전히 무시하고 학습 내용만 설명하세요.
                    6. 응답 마지막 줄에 다음 태그 중 하나를 [TAG:XXX] 형식으로 붙여주세요:
                       DEFINITION(정의), KEY_CONCEPT(핵심개념), EXAM_POINT(시험포인트), CONFUSING(자주 헷갈림), NONE(일반)
                    """;

            String userMessage = "다음 PDF 원문 내용을 강의하듯 설명해주세요:\n\n" + blockContent;

            String aiResponse = claudeApiService.callClaude(systemPrompt, userMessage);

            Explanation.ExplanationTag tag = extractTag(aiResponse);
            String content = removeTagFromResponse(aiResponse);

            Explanation explanation = Explanation.builder()
                    .document(document)
                    .blockIds(blockIdsStr)
                    .explanationOrder(order)
                    .content(content)
                    .tag(tag)
                    .build();

            explanationRepository.save(explanation);
        }

        document.setStatus(Document.DocumentStatus.READY);
        documentRepository.save(document);
        log.info("해설 생성 완료 - 문서 {}: {}개 해설", documentId, order);
    }

    @Transactional(readOnly = true)
    public ExplanationListResponse getExplanations(Long documentId, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("문서를 찾을 수 없습니다."));

        if (!document.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 문서에 대한 접근 권한이 없습니다.");
        }

        List<Explanation> explanations = explanationRepository.findByDocumentIdOrderByExplanationOrderAsc(documentId);
        List<ExplanationResponse> responses = explanations.stream()
                .map(ExplanationResponse::from)
                .collect(Collectors.toList());
        return ExplanationListResponse.builder().explanations(responses).build();
    }

    @Transactional
    public ExplanationResponse regenerate(Long documentId, Long explanationId, RegenerateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        checkRegenerateLimit(user, documentId);

        Explanation explanation = explanationRepository.findById(explanationId)
                .orElseThrow(() -> ApiException.notFound("해설을 찾을 수 없습니다."));

        if (!explanation.getDocument().getId().equals(documentId)) {
            throw ApiException.badRequest("해당 문서의 해설이 아닙니다.");
        }

        List<PdfBlock> relatedBlocks = getRelatedBlocks(explanation);
        String blockContent = relatedBlocks.stream()
                .map(PdfBlock::getContent)
                .collect(Collectors.joining("\n\n"));

        String mode = request.getMode().toUpperCase();
        String systemPrompt = buildRegeneratePrompt(mode);
        String userMessage = "다음 PDF 원문 내용에 대해 설명해주세요:\n\n" + blockContent;

        String aiResponse = claudeApiService.callClaude(systemPrompt, userMessage);

        Explanation.ExplanationTag tag = extractTag(aiResponse);
        String content = removeTagFromResponse(aiResponse);

        explanation.setContent(content);
        explanation.setTag(tag);
        explanation.setRegeneratedAt(LocalDateTime.now());
        explanationRepository.save(explanation);

        return ExplanationResponse.from(explanation);
    }

    private void checkRegenerateLimit(User user, Long documentId) {
        if (user.getRole() == User.UserRole.FREE) {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            long todayCount = explanationRepository
                    .countByDocument_User_IdAndRegeneratedAtIsNotNullAndRegeneratedAtAfter(user.getId(), todayStart);
            if (todayCount >= FREE_DAILY_REGENERATE_LIMIT) {
                throw ApiException.freeLimitExceeded("무료 사용자는 해설 재생성을 하루 " + FREE_DAILY_REGENERATE_LIMIT + "회까지 사용할 수 있습니다.");
            }
        }
    }

    private List<PdfBlock> getRelatedBlocks(Explanation explanation) {
        if (explanation.getBlockIds() == null || explanation.getBlockIds().isBlank()) {
            return List.of();
        }
        String[] ids = explanation.getBlockIds().split(",");
        List<PdfBlock> blocks = new ArrayList<>();
        for (String id : ids) {
            pdfBlockRepository.findById(Long.parseLong(id.trim())).ifPresent(blocks::add);
        }
        return blocks;
    }

    private String buildRegeneratePrompt(String mode) {
        return switch (mode) {
            case "SIMPLE" -> """
                    당신은 대학교 교수입니다. 다음 내용을 고등학생도 이해할 수 있도록 최대한 쉽게 설명해주세요.
                    전문 용어를 피하고, 비유를 활용해주세요.
                    응답 마지막 줄에 [TAG:XXX] 형식으로 태그를 붙여주세요.
                    """;
            case "DETAILED" -> """
                    당신은 대학교 교수입니다. 다음 내용을 상세하게 설명해주세요.
                    배경 지식, 수학적 유도 과정, 응용 사례까지 포함해주세요.
                    응답 마지막 줄에 [TAG:XXX] 형식으로 태그를 붙여주세요.
                    """;
            case "EXAMPLE" -> """
                    당신은 대학교 교수입니다. 다음 내용에 대해 구체적인 예시를 들어 설명해주세요.
                    실생활 예시, 계산 예시, 코드 예시 등을 포함해주세요.
                    응답 마지막 줄에 [TAG:XXX] 형식으로 태그를 붙여주세요.
                    """;
            case "DIAGRAM" -> """
                    당신은 대학교 교수입니다. 다음 내용의 핵심 개념을 Mermaid 다이어그램 문법으로 시각화해주세요.
                    반드시 ```mermaid 코드 블록 안에 Mermaid 문법(flowchart, mindmap, graph 등)으로 작성하세요.
                    다이어그램 아래에 한 줄 요약 설명을 추가하세요.
                    응답 마지막 줄에 [TAG:XXX] 형식으로 태그를 붙여주세요.
                    """;
            default -> """
                    당신은 대학교 교수입니다. 학생에게 강의하듯 설명해주세요.
                    응답 마지막 줄에 [TAG:XXX] 형식으로 태그를 붙여주세요.
                    """;
        };
    }

    private Explanation.ExplanationTag extractTag(String response) {
        if (response.contains("[TAG:DEFINITION]")) return Explanation.ExplanationTag.DEFINITION;
        if (response.contains("[TAG:KEY_CONCEPT]")) return Explanation.ExplanationTag.KEY_CONCEPT;
        if (response.contains("[TAG:EXAM_POINT]")) return Explanation.ExplanationTag.EXAM_POINT;
        if (response.contains("[TAG:CONFUSING]")) return Explanation.ExplanationTag.CONFUSING;
        return Explanation.ExplanationTag.NONE;
    }

    private String removeTagFromResponse(String response) {
        return response.replaceAll("\\[TAG:[A-Z_]+]", "").trim();
    }

    /**
     * 블록 그룹핑: HEADING 기반 섹션 분할 + 크기 제한.
     * - HEADING 블록을 만나면 새 그룹 시작
     * - 그룹 텍스트 합산 200자 미만이면 다음 그룹과 병합
     * - 그룹 텍스트 합산 3000자 초과하면 강제 분할
     * - HEADING이 없으면 기존 로직(1500자/5블록) 폴백
     */
    private List<List<PdfBlock>> groupBlocks(List<PdfBlock> blocks) {
        // HEADING 존재 여부 확인
        boolean hasHeading = blocks.stream()
                .anyMatch(b -> b.getBlockType() == PdfBlock.BlockType.HEADING);

        if (!hasHeading) {
            return groupBlocksFallback(blocks);
        }

        return groupBlocksByHeading(blocks);
    }

    /**
     * HEADING 기반 그룹핑
     */
    private List<List<PdfBlock>> groupBlocksByHeading(List<PdfBlock> blocks) {
        List<List<PdfBlock>> rawGroups = new ArrayList<>();
        List<PdfBlock> currentGroup = new ArrayList<>();

        for (PdfBlock block : blocks) {
            // HEADING을 만나면 현재 그룹을 마감하고 새 그룹 시작
            if (block.getBlockType() == PdfBlock.BlockType.HEADING && !currentGroup.isEmpty()) {
                rawGroups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
            }
            currentGroup.add(block);
        }
        if (!currentGroup.isEmpty()) {
            rawGroups.add(currentGroup);
        }

        // 병합(200자 미만) + 강제 분할(3000자 초과)
        List<List<PdfBlock>> finalGroups = new ArrayList<>();
        for (List<PdfBlock> group : rawGroups) {
            int groupLen = groupTextLength(group);

            // 200자 미만이면 이전 그룹과 병합 시도
            if (groupLen < 200 && !finalGroups.isEmpty()) {
                List<PdfBlock> prev = finalGroups.get(finalGroups.size() - 1);
                int mergedLen = groupTextLength(prev) + groupLen;
                if (mergedLen <= 3000) {
                    prev.addAll(group);
                    continue;
                }
            }

            // 3000자 초과면 강제 분할
            if (groupLen > 3000) {
                List<PdfBlock> subGroup = new ArrayList<>();
                for (PdfBlock b : group) {
                    subGroup.add(b);
                    if (groupTextLength(subGroup) > 3000) {
                        // 현재 블록을 포함해서 분할
                        finalGroups.add(new ArrayList<>(subGroup));
                        subGroup.clear();
                    }
                }
                if (!subGroup.isEmpty()) {
                    finalGroups.add(subGroup);
                }
            } else {
                finalGroups.add(group);
            }
        }

        return finalGroups;
    }

    /**
     * 기존 로직 폴백: 1500자/5블록 기준 기계적 분할
     */
    private List<List<PdfBlock>> groupBlocksFallback(List<PdfBlock> blocks) {
        List<List<PdfBlock>> groups = new ArrayList<>();
        List<PdfBlock> currentGroup = new ArrayList<>();
        int currentPage = -1;

        for (PdfBlock block : blocks) {
            if (block.getPageNumber() != currentPage && !currentGroup.isEmpty()) {
                if (currentGroup.size() >= 3) {
                    groups.add(new ArrayList<>(currentGroup));
                    currentGroup.clear();
                }
            }

            currentGroup.add(block);
            currentPage = block.getPageNumber();

            String totalText = currentGroup.stream()
                    .map(PdfBlock::getContent)
                    .collect(Collectors.joining(" "));
            if (totalText.length() > 1500 || currentGroup.size() >= 5) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
            }
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    private int groupTextLength(List<PdfBlock> group) {
        return group.stream()
                .mapToInt(b -> b.getContent() != null ? b.getContent().length() : 0)
                .sum();
    }
}
