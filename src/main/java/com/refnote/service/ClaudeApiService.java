package com.refnote.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class ClaudeApiService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    @Value("${claude.api-key:}")
    private String apiKey;

    @Value("${claude.model:claude-haiku-4-5-20250401}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String callClaude(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Claude API 키가 설정되지 않았습니다. Mock 응답을 반환합니다.");
            return generateMockResponse(systemPrompt, userMessage);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", 4096);
            body.put("system", systemPrompt);
            body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(CLAUDE_API_URL, HttpMethod.POST, request, Map.class);

            if (response.getBody() != null) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
                if (content != null && !content.isEmpty()) {
                    return (String) content.get(0).get("text");
                }
            }

            return "해설을 생성할 수 없습니다.";
        } catch (Exception e) {
            log.error("Claude API 호출 실패", e);
            return generateMockResponse(systemPrompt, userMessage);
        }
    }

    private String generateMockResponse(String systemPrompt, String userMessage) {
        // DIAGRAM 모드 판별 (Mermaid 다이어그램 요청)
        if (systemPrompt.contains("Mermaid") || systemPrompt.contains("다이어그램 문법")) {
            return generateMockDiagramResponse();
        }
        // 해설 생성 요청인지 채팅 요청인지 판별
        if (systemPrompt.contains("강의하듯 설명") || systemPrompt.contains("설명해주세요")) {
            return generateMockExplanation(systemPrompt, userMessage);
        }
        if (systemPrompt.contains("AI 학습 튜터") || systemPrompt.contains("사용자 질문")) {
            return generateMockChatResponse(userMessage);
        }
        // 기본 해설 mock
        return generateMockExplanation(systemPrompt, userMessage);
    }

    private String generateMockDiagramResponse() {
        return """
                ```mermaid
                flowchart TD
                    A[핵심 개념] --> B[하위 개념 1]
                    A --> C[하위 개념 2]
                    B --> D[세부 항목]
                    C --> D
                ```
                이 다이어그램은 핵심 개념들의 관계를 보여줍니다.
                (Claude API 미연결 상태의 Mock 다이어그램입니다.)
                [TAG:KEY_CONCEPT]""";
    }

    private String generateMockExplanation(String systemPrompt, String userMessage) {
        // userMessage에서 원문 텍스트 추출 (프롬프트 접두사 제거)
        String blockContent = userMessage;
        if (blockContent.contains(":\n\n")) {
            blockContent = blockContent.substring(blockContent.indexOf(":\n\n") + 3);
        }

        // 텍스트가 너무 길면 앞부분만 사용
        String summary = blockContent.length() > 200
                ? blockContent.substring(0, 200) + "..."
                : blockContent;

        // ExplanationService의 extractTag()가 파싱할 수 있는 [TAG:XXX] 형식 포함
        String tag = detectMockTag(summary);

        return String.format(
                "이 내용은 다음과 같은 핵심 개념을 다루고 있습니다.\n\n" +
                "**원문 요약**: %s\n\n" +
                "쉽게 설명하면, 이 부분은 해당 주제의 기본 원리를 설명하고 있습니다. " +
                "원문의 내용을 잘 이해하기 위해서는 관련 배경 지식을 함께 학습하는 것이 좋습니다.\n\n" +
                "(Claude API 미연결 상태의 Mock 해설입니다. 실제 API 키를 설정하면 더 정확한 해설이 생성됩니다.)\n\n" +
                "[TAG:%s]", summary, tag);
    }

    private String generateMockChatResponse(String userMessage) {
        // userMessage에서 실제 질문 부분 추출
        String question = userMessage;
        if (userMessage.contains("사용자 질문: ")) {
            question = userMessage.substring(userMessage.lastIndexOf("사용자 질문: ") + "사용자 질문: ".length());
        }

        // ChatService의 extractBlockIds()가 파싱할 수 있는 [BLOCKS:x,y,z] 형식 포함
        return String.format(
                "'%s'에 대한 답변입니다.\n\n" +
                "해당 질문은 문서의 내용과 관련이 있습니다. " +
                "원문을 참고하여 관련 개념을 확인해 보시기 바랍니다.\n\n" +
                "(Claude API 미연결 상태의 Mock 응답입니다. 실제 API 키를 설정하면 문서 기반의 정확한 답변이 제공됩니다.)",
                question.length() > 100 ? question.substring(0, 100) + "..." : question);
    }

    private String detectMockTag(String text) {
        // 간단한 키워드 기반 태그 추정
        String lower = text.toLowerCase();
        if (lower.contains("정의") || lower.contains("definition") || lower.contains("이란")) {
            return "DEFINITION";
        }
        if (lower.contains("시험") || lower.contains("출제") || lower.contains("기출")) {
            return "EXAM_POINT";
        }
        if (lower.contains("주의") || lower.contains("헷갈") || lower.contains("오해")) {
            return "CONFUSING";
        }
        return "KEY_CONCEPT";
    }
}
