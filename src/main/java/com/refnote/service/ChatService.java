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

    private static final int FREE_DAILY_CHAT_LIMIT = 5;

    @Transactional
    public ChatResponse chat(Long documentId, ChatRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("문서를 찾을 수 없습니다."));

        if (!document.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 문서에 대한 접근 권한이 없습니다.");
        }

        checkChatLimit(user);

        ChatMessage userMessage = ChatMessage.builder()
                .document(document)
                .user(user)
                .role(ChatMessage.MessageRole.USER)
                .content(request.getMessage())
                .build();
        chatMessageRepository.save(userMessage);

        String context = buildContext(documentId, request.getCurrentExplanationId());

        String systemPrompt = """
                당신은 AI 학습 튜터입니다. 사용자가 학습 중인 PDF 문서에 대해 질문합니다.
                다음 규칙을 따르세요:
                1. 제공된 PDF 원문 내용을 기반으로만 답변하세요.
                2. 답변이 관련된 PDF 블록 ID가 있다면, 응답 마지막에 [BLOCKS:1,2,3] 형식으로 표시해주세요.
                3. 원문에 없는 내용은 "원문에서 관련 내용을 찾을 수 없습니다."라고 안내하세요.
                4. 학생 눈높이에 맞춰 친절하게 설명하세요.
                """;

        String userPrompt = "PDF 원문 컨텍스트:\n" + context + "\n\n사용자 질문: " + request.getMessage();

        String aiResponseText = claudeApiService.callClaude(systemPrompt, userPrompt);

        List<Long> relatedBlockIds = extractBlockIds(aiResponseText);
        String cleanResponse = aiResponseText.replaceAll("\\[BLOCKS:[\\d,\\s]+]", "").trim();

        String blockIdsStr = relatedBlockIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        ChatMessage aiMessage = ChatMessage.builder()
                .document(document)
                .user(user)
                .role(ChatMessage.MessageRole.AI)
                .content(cleanResponse)
                .relatedBlockIds(blockIdsStr.isEmpty() ? null : blockIdsStr)
                .build();
        chatMessageRepository.save(aiMessage);

        return ChatResponse.builder()
                .id(aiMessage.getId())
                .content(cleanResponse)
                .relatedBlockIds(relatedBlockIds)
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

    private String buildContext(Long documentId, Long currentExplanationId) {
        StringBuilder context = new StringBuilder();

        if (currentExplanationId != null) {
            explanationRepository.findById(currentExplanationId).ifPresent(explanation -> {
                context.append("현재 학습 중인 해설:\n").append(explanation.getContent()).append("\n\n");

                if (explanation.getBlockIds() != null && !explanation.getBlockIds().isBlank()) {
                    String[] ids = explanation.getBlockIds().split(",");
                    context.append("관련 원문 블록:\n");
                    for (String id : ids) {
                        pdfBlockRepository.findById(Long.parseLong(id.trim())).ifPresent(block ->
                                context.append("[블록 ").append(block.getId()).append("] ").append(block.getContent()).append("\n\n"));
                    }
                }
            });
        }

        List<PdfBlock> allBlocks = pdfBlockRepository.findByDocumentIdOrderByPageNumberAscBlockOrderAsc(documentId);
        context.append("전체 문서 원문:\n");
        int charCount = 0;
        for (PdfBlock block : allBlocks) {
            String blockText = "[블록 " + block.getId() + "] " + block.getContent() + "\n\n";
            charCount += blockText.length();
            if (charCount > 10000) break;
            context.append(blockText);
        }

        return context.toString();
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
}
