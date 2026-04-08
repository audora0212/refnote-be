package com.refnote.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.refnote.dto.review.QuizResponse;

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
        // AI 블록 분리 모드 판별
        if (systemPrompt.contains("학습 단위(섹션/주제)로 나눠주세요")) {
            return generateMockBlockSplit(userMessage);
        }
        // 분류 모드 판별
        if (systemPrompt.contains("학습자료 분류 전문가")) {
            return "{\"isStudyMaterial\":true,\"estimatedSubject\":\"컴퓨터공학\",\"estimatedDifficulty\":\"INTERMEDIATE\",\"documentType\":\"TEXTBOOK\"}";
        }
        // 퀴즈 모드 판별
        if (systemPrompt.contains("OX 퀴즈를 만드는") || systemPrompt.contains("빈칸 채우기 퀴즈를 만드는")) {
            return generateMockQuizJson(systemPrompt);
        }
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

    private String generateMockBlockSplit(String pageText) {
        // Mock: 텍스트를 줄바꿈 2개 기준으로 단락 분리, 각각 TEXT 블록으로 반환
        String[] paragraphs = pageText.split("\\n\\s*\\n");
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            if (!first) sb.append(",");
            first = false;
            // JSON escape
            String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            // 짧은 텍스트는 HEADING으로 추정
            String type = trimmed.length() < 80 ? "HEADING" : "TEXT";
            sb.append("{\"type\":\"").append(type).append("\",\"content\":\"").append(escaped).append("\"}");
        }
        sb.append("]");
        return sb.toString();
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

    private String generateMockQuizJson(String systemPrompt) {
        if (systemPrompt.contains("OX 퀴즈를 만드는")) {
            return "{\"question\":\"이 개념은 해당 학습 내용의 핵심 원리에 해당한다. (O/X)\",\"answer\":\"O\",\"explanation\":\"해당 블록에서 다루고 있는 내용은 주제의 핵심 원리입니다. (Mock 퀴즈)\"}";
        } else {
            return "{\"question\":\"해당 학습 내용에서 다루는 핵심 개념을 ___이라 한다.\",\"answer\":\"핵심 원리\",\"explanation\":\"원문에서 설명하는 주요 개념입니다. (Mock 퀴즈)\"}";
        }
    }

    // === 미니 퀴즈 생성 (v4) ===

    /**
     * 학습 내용을 기반으로 미니 퀴즈 1개를 생성한다.
     * Claude API가 없으면 mock 퀴즈를 반환한다.
     *
     * @param blockContent  복습 대상 블록의 원문 텍스트
     * @param explanationContent  해설 내용 (null 가능)
     * @param sourceBlockId  원본 블록 ID
     * @return QuizResponse
     */
    public QuizResponse generateQuiz(String blockContent, String explanationContent, Long sourceBlockId) {
        // 퀴즈 유형 랜덤 선택
        String quizType = new Random().nextBoolean() ? "OX" : "FILL_BLANK";

        String systemPrompt = buildQuizSystemPrompt(quizType);
        String userMessage = buildQuizUserMessage(blockContent, explanationContent);

        String response = callClaude(systemPrompt, userMessage);
        return parseQuizResponse(response, quizType, sourceBlockId);
    }

    private String buildQuizSystemPrompt(String quizType) {
        if ("OX".equals(quizType)) {
            return """
                당신은 학습 내용을 기반으로 OX 퀴즈를 만드는 전문가입니다.
                제공된 학습 내용을 바탕으로 OX 문제 1개를 생성하세요.

                반드시 아래 JSON 형식으로만 응답하세요 (다른 텍스트 없이 JSON만):
                {"question":"...내용... (O/X)","answer":"O 또는 X","explanation":"정답 해설"}

                규칙:
                - 질문은 반드시 참/거짓을 판단할 수 있는 명제여야 합니다
                - 질문 끝에 (O/X)를 붙이세요
                - answer는 "O" 또는 "X" 중 하나
                - explanation은 왜 그 답이 맞는지 1-2문장으로 설명
                """;
        } else {
            return """
                당신은 학습 내용을 기반으로 빈칸 채우기 퀴즈를 만드는 전문가입니다.
                제공된 학습 내용을 바탕으로 빈칸 문제 1개를 생성하세요.

                반드시 아래 JSON 형식으로만 응답하세요 (다른 텍스트 없이 JSON만):
                {"question":"...___... 을(를) 무엇이라 하는가?","answer":"정답 단어","explanation":"정답 해설"}

                규칙:
                - 질문에서 핵심 용어 하나를 ___로 대체하세요
                - answer는 빈칸에 들어갈 정확한 답
                - explanation은 왜 그 답이 맞는지 1-2문장으로 설명
                """;
        }
    }

    private String buildQuizUserMessage(String blockContent, String explanationContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 학습 내용을 기반으로 퀴즈를 만들어주세요:\n\n");
        sb.append("【원문】\n").append(blockContent).append("\n");
        if (explanationContent != null && !explanationContent.isBlank()) {
            sb.append("\n【해설】\n").append(explanationContent).append("\n");
        }
        return sb.toString();
    }

    private QuizResponse parseQuizResponse(String response, String quizType, Long sourceBlockId) {
        try {
            String json = response.trim();
            int jsonStart = json.indexOf('{');
            int jsonEnd = json.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = json.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode node = objectMapper.readTree(json);

            return QuizResponse.builder()
                    .type(quizType)
                    .question(node.path("question").asText(""))
                    .answer(node.path("answer").asText(""))
                    .explanation(node.path("explanation").asText(""))
                    .sourceBlockId(sourceBlockId)
                    .build();
        } catch (Exception e) {
            log.warn("퀴즈 응답 파싱 실패, mock 퀴즈 반환: {}", e.getMessage());
            return generateMockQuiz(quizType, sourceBlockId);
        }
    }

    private QuizResponse generateMockQuiz(String quizType, Long sourceBlockId) {
        if ("OX".equals(quizType)) {
            return QuizResponse.builder()
                    .type("OX")
                    .question("이 개념은 해당 학습 내용의 핵심 원리에 해당한다. (O/X)")
                    .answer("O")
                    .explanation("해당 블록에서 다루고 있는 내용은 주제의 핵심 원리입니다. (Mock 퀴즈)")
                    .sourceBlockId(sourceBlockId)
                    .build();
        } else {
            return QuizResponse.builder()
                    .type("FILL_BLANK")
                    .question("해당 학습 내용에서 다루는 핵심 개념을 ___이라 한다.")
                    .answer("핵심 원리")
                    .explanation("원문에서 설명하는 주요 개념입니다. (Mock 퀴즈)")
                    .sourceBlockId(sourceBlockId)
                    .build();
        }
    }

    // === AI 블록 분리 기능 ===

    public record SplitBlock(String type, String content) {}

    /**
     * 페이지 텍스트를 AI에게 학습 단위로 분리 요청.
     * 실패 시 null 반환 (호출부에서 fallback 처리).
     */
    public List<SplitBlock> splitBlocksWithAI(String pageText) {
        String systemPrompt = """
                당신은 PDF 학습자료를 분석하는 전문가입니다.
                주어진 페이지 텍스트를 학습 단위(섹션/주제)로 나눠주세요.

                규칙:
                1. 하나의 주제나 개념을 다루는 단위로 블록을 나누세요.
                2. 제목/소제목은 type: "HEADING"으로 표시하세요.
                3. 학습 내용은 type: "TEXT"로 표시하세요.
                4. 수식이 포함된 블록은 type: "FORMULA"로 표시하세요.
                5. 표가 포함된 블록은 type: "TABLE"로 표시하세요.
                6. 교수 이름, 이메일, 전화번호, 홈페이지, 강의실, 학과 사무실, 연구실 위치, 수업 시간, 출석/성적 비율 등의 메타데이터는 type: "SKIP"으로 표시하세요.
                7. 페이지 번호, 머리글/바닥글 등 반복되는 장식 텍스트도 type: "SKIP"으로 표시하세요.

                반드시 아래 JSON 배열 형식으로만 응답하세요 (다른 텍스트 없이 JSON만):
                [{"type":"HEADING","content":"..."},{"type":"TEXT","content":"..."},{"type":"SKIP","content":"..."}]
                """;

        try {
            String response = callClaude(systemPrompt, pageText);
            // JSON 배열 부분만 추출
            String json = response.trim();
            int arrStart = json.indexOf('[');
            int arrEnd = json.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                json = json.substring(arrStart, arrEnd + 1);
            }

            List<SplitBlock> blocks = objectMapper.readValue(json, new TypeReference<List<SplitBlock>>() {});
            // 유효성 검증: 빈 결과면 null 반환
            if (blocks == null || blocks.isEmpty()) {
                return null;
            }
            return blocks;
        } catch (Exception e) {
            log.warn("AI 블록 분리 실패, heuristic 폴백 사용: {}", e.getMessage());
            return null;
        }
    }

    // === 문서 분류 기능 (v2) ===

    public record ClassificationResult(
            boolean isStudyMaterial,
            String estimatedSubject,
            String estimatedDifficulty,
            String documentType,
            String rejectionReason
    ) {}

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ClassificationResult classifyDocument(String sampleText) {
        String systemPrompt = """
                당신은 학습자료 분류 전문가입니다.
                사용자가 제공한 문서 텍스트를 분석하여 학습자료인지 판별하세요.

                반드시 아래 JSON 형식으로만 응답하세요 (다른 텍스트 없이 JSON만):

                학습자료인 경우:
                {"isStudyMaterial":true,"estimatedSubject":"과목명","estimatedDifficulty":"BEGINNER|INTERMEDIATE|ADVANCED","documentType":"TEXTBOOK|LECTURE_NOTE|PAPER|EXAM|OTHER"}

                학습자료가 아닌 경우:
                {"isStudyMaterial":false,"rejectionReason":"사유"}

                판별 기준:
                - 학습자료: 교재, 강의노트, 논문, 시험지, 학술 자료 등
                - 비학습자료: 소설, 이력서, 계약서, 영수증, 광고, 이미지만 있는 PDF 등
                """;

        String response = callClaude(systemPrompt, sampleText);
        return parseClassificationResponse(response);
    }

    private ClassificationResult parseClassificationResponse(String response) {
        try {
            // JSON 부분만 추출 (앞뒤 텍스트가 있을 수 있음)
            String json = response.trim();
            int jsonStart = json.indexOf('{');
            int jsonEnd = json.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = json.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode node = objectMapper.readTree(json);

            boolean isStudyMaterial = node.path("isStudyMaterial").asBoolean(true);

            if (isStudyMaterial) {
                return new ClassificationResult(
                        true,
                        node.path("estimatedSubject").asText(null),
                        node.path("estimatedDifficulty").asText(null),
                        node.path("documentType").asText(null),
                        null
                );
            } else {
                return new ClassificationResult(
                        false,
                        null,
                        null,
                        null,
                        node.path("rejectionReason").asText("학습자료로 적합하지 않은 문서입니다.")
                );
            }
        } catch (Exception e) {
            log.warn("분류 응답 파싱 실패, 안전하게 비학습자료로 처리: {}", e.getMessage());
            return new ClassificationResult(false, null, null, null, "AI 분류 응답을 파싱할 수 없습니다. 다시 업로드해주세요.");
        }
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
