package com.refnote.service;

import com.refnote.dto.chat.ChatHistoryResponse;
import com.refnote.dto.chat.ChatRequest;
import com.refnote.dto.chat.ChatResponse;
import com.refnote.entity.*;
import com.refnote.exception.ApiException;
import com.refnote.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.refnote.entity.Confidence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfBlockRepository pdfBlockRepository;
    private final ExplanationRepository explanationRepository;
    private final ClaudeApiService claudeApiService;
    private final ReviewQueueService reviewQueueService;

    private static final int FREE_DAILY_CHAT_LIMIT = 5;

    @Transactional
    public ChatResponse chat(Long documentId, ChatRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        checkChatLimit(user);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("문서를 찾을 수 없습니다."));

        if (!document.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 문서에 대한 접근 권한이 없습니다.");
        }

        ChatMessage userMessage = ChatMessage.builder()
                .document(document)
                .user(user)
                .role(ChatMessage.MessageRole.USER)
                .content(request.getMessage())
                .build();
        chatMessageRepository.save(userMessage);

        String context = buildContext(documentId, request.getCurrentExplanationId(), request.getMessage());

        String systemPrompt = """
                당신은 AI 학습 튜터입니다. 사용자가 학습 중인 PDF 문서에 대해 질문합니다.
                다음 규칙을 반드시 따르세요:
                1. 제공된 PDF 원문 텍스트 블록 안에서만 답변하세요. 외부 지식을 사용하지 마세요.
                2. 답변이 관련된 PDF 블록 ID가 있다면, 응답 마지막에 [BLOCKS:1,2,3] 형식으로 근거 블록 ID를 명시하세요.
                3. 원문에 없는 내용이면 "이 문서에서 관련 내용을 찾을 수 없습니다. 다른 질문을 해주세요."라고 안내하세요.
                4. 학생 눈높이에 맞춰 친절하게 설명하세요.
                5. 답변의 신뢰도를 마지막에 [CONFIDENCE:HIGH], [CONFIDENCE:MEDIUM], 또는 [CONFIDENCE:LOW]로 표시하세요.
                   - HIGH: 원문에서 명확한 근거를 찾은 경우
                   - MEDIUM: 원문에서 관련 내용은 있지만 직접적인 답변은 아닌 경우
                   - LOW: 원문에서 관련 내용을 거의 찾지 못한 경우
                """;

        String userPrompt = "PDF 원문 컨텍스트:\n" + context + "\n\n사용자 질문: " + request.getMessage();

        String aiResponseText = claudeApiService.callClaude(systemPrompt, userPrompt);

        List<Long> relatedBlockIds = extractBlockIds(aiResponseText);
        Confidence confidence = extractConfidence(aiResponseText);
        String cleanResponse = aiResponseText
                .replaceAll("\\[BLOCKS:[\\d,\\s]+]", "")
                .replaceAll("\\[CONFIDENCE:(HIGH|MEDIUM|LOW)]", "")
                .trim();

        String blockIdsStr = relatedBlockIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<Long> sourceExplanationIds = findRelatedExplanationIds(documentId, relatedBlockIds);

        ChatMessage aiMessage = ChatMessage.builder()
                .document(document)
                .user(user)
                .role(ChatMessage.MessageRole.AI)
                .content(cleanResponse)
                .relatedBlockIds(blockIdsStr.isEmpty() ? null : blockIdsStr)
                .confidence(confidence)
                .build();
        chatMessageRepository.save(aiMessage);

        reviewQueueService.onQuestionAsked(userMessage);

        return ChatResponse.builder()
                .id(aiMessage.getId())
                .content(cleanResponse)
                .relatedBlockIds(relatedBlockIds)
                .sourceExplanationIds(sourceExplanationIds)
                .confidence(confidence != null ? confidence.name() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(Long documentId, Long userId) {
        List<ChatMessage> messages = chatMessageRepository
                .findByDocumentIdAndUserIdOrderByCreatedAtAsc(documentId, userId);

        List<ChatHistoryResponse.ChatMessageDto> dtos = messages.stream()
                .map(ChatHistoryResponse.ChatMessageDto::from)
                .collect(Collectors.toList());

        return ChatHistoryResponse.builder().messages(dtos).build();
    }

    private void checkChatLimit(User user) {
        if (user.getRole() == User.UserRole.FREE) {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            long todayCount = chatMessageRepository.countByUserIdAndRoleAndCreatedAtAfter(
                    user.getId(), ChatMessage.MessageRole.USER, todayStart);
            if (todayCount >= FREE_DAILY_CHAT_LIMIT) {
                throw ApiException.freeLimitExceeded("무료 사용자는 추가질문을 하루 " + FREE_DAILY_CHAT_LIMIT + "회까지 사용할 수 있습니다.");
            }
        }
    }

    /**
     * 컨텍스트 구성: 현재 해설 + 관련 블록 + 키워드 매칭 블록 + 나머지 순차 블록 (총 10000자 제한)
     */
    private String buildContext(Long documentId, Long currentExplanationId, String userQuestion) {
        StringBuilder context = new StringBuilder();
        Set<Long> usedBlockIds = new HashSet<>();
        final int MAX_CONTEXT_LENGTH = 10000;

        // 1단계: 현재 해설 + 관련 블록
        if (currentExplanationId != null) {
            explanationRepository.findById(currentExplanationId).ifPresent(explanation -> {
                context.append("현재 학습 중인 해설:\n").append(explanation.getContent()).append("\n\n");

                if (explanation.getBlockIds() != null && !explanation.getBlockIds().isBlank()) {
                    String[] ids = explanation.getBlockIds().split(",");
                    context.append("관련 원문 블록:\n");
                    for (String id : ids) {
                        long blockId = Long.parseLong(id.trim());
                        pdfBlockRepository.findById(blockId).ifPresent(block -> {
                            context.append("[블록 ").append(block.getId()).append("] ").append(block.getContent()).append("\n\n");
                            usedBlockIds.add(block.getId());
                        });
                    }
                }
            });
        }

        List<PdfBlock> allBlocks = pdfBlockRepository.findByDocumentIdOrderByPageNumberAscBlockOrderAsc(documentId);

        // 2단계: 질문 키워드로 블록 검색
        List<String> keywords = extractKeywords(userQuestion);
        if (!keywords.isEmpty()) {
            List<PdfBlock> keywordMatchedBlocks = allBlocks.stream()
                    .filter(block -> !usedBlockIds.contains(block.getId()))
                    .filter(block -> {
                        String content = block.getContent().toLowerCase();
                        return keywords.stream().anyMatch(kw -> content.contains(kw.toLowerCase()));
                    })
                    .collect(Collectors.toList());

            if (!keywordMatchedBlocks.isEmpty()) {
                context.append("질문 관련 블록:\n");
                for (PdfBlock block : keywordMatchedBlocks) {
                    if (context.length() >= MAX_CONTEXT_LENGTH) break;
                    String blockText = "[블록 " + block.getId() + "] " + block.getContent() + "\n\n";
                    context.append(blockText);
                    usedBlockIds.add(block.getId());
                }
            }
        }

        // 3단계: 나머지 순차 블록
        if (context.length() < MAX_CONTEXT_LENGTH) {
            context.append("전체 문서 원문:\n");
            for (PdfBlock block : allBlocks) {
                if (usedBlockIds.contains(block.getId())) continue;
                if (context.length() >= MAX_CONTEXT_LENGTH) break;
                String blockText = "[블록 " + block.getId() + "] " + block.getContent() + "\n\n";
                context.append(blockText);
            }
        }

        return context.toString();
    }

    /**
     * 질문에서 핵심 키워드 추출 (2글자 이상, 불용어 제외)
     */
    private List<String> extractKeywords(String question) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }

        // 한국어 조사/불용어
        Set<String> stopWords = Set.of(
                "이", "가", "은", "는", "을", "를", "의", "에", "에서", "로", "으로",
                "와", "과", "도", "만", "부터", "까지", "대해", "대한", "대해서",
                "것", "수", "등", "및", "또는", "그리고", "하지만", "그래서",
                "무엇", "어떤", "어떻게", "왜", "언제", "어디",
                "해주세요", "설명해", "알려", "설명", "질문",
                "좀", "더", "다시", "이것", "저것", "그것"
        );

        // 공백/특수문자로 분리 후 2글자 이상 & 불용어 아닌 것만 추출
        return Arrays.stream(question.split("[\\s,.?!;:()\\[\\]{}\"']+"))
                .map(String::trim)
                .filter(w -> w.length() >= 2)
                .filter(w -> !stopWords.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> extractBlockIds(String response) {
        List<Long> blockIds = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[BLOCKS:([\\d,\\s]+)]");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String idsStr = matcher.group(1);
            for (String id : idsStr.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    blockIds.add(Long.parseLong(trimmed));
                }
            }
        }
        return blockIds;
    }

    private Confidence extractConfidence(String response) {
        Pattern pattern = Pattern.compile("\\[CONFIDENCE:(HIGH|MEDIUM|LOW)]");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return Confidence.valueOf(matcher.group(1));
        }
        return Confidence.MEDIUM;
    }

    private List<Long> findRelatedExplanationIds(Long documentId, List<Long> blockIds) {
        if (blockIds.isEmpty()) {
            return Collections.emptyList();
        }

        return explanationRepository.findByDocumentIdOrderByExplanationOrderAsc(documentId).stream()
                .filter(exp -> {
                    if (exp.getBlockIds() == null || exp.getBlockIds().isBlank()) return false;
                    Set<String> expBlockIds = Set.of(exp.getBlockIds().split(","));
                    return blockIds.stream().anyMatch(bid -> expBlockIds.contains(bid.toString().trim()));
                })
                .map(exp -> exp.getId())
                .collect(Collectors.toList());
    }
}
