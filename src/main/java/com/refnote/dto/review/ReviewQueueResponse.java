package com.refnote.dto.review;

import com.refnote.entity.ReviewQueueItem;
import com.refnote.entity.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
@Builder
public class ReviewQueueResponse {
    private List<ReviewQueueItemDto> items;
    private long totalCount;
    private long pendingCount;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class ReviewQueueItemDto {
        private Long id;
        private Long documentId;
        private String documentTitle;
        private String subjectName;
        private Long blockId;
        private String blockContent;
        private String sourceType;
        private String sourceDetail;
        private String title;
        private int priority;
        private String status;
        private int deferCount;
        private int confusedCount;
        private LocalDate scheduledDate;
        private LocalDateTime createdAt;
        private String studyLink;
        private String reason;

        /**
         * 기본 변환 (단일 항목 기준 reason 생성)
         */
        public static ReviewQueueItemDto from(ReviewQueueItem item) {
            return from(item, Set.of());
        }

        /**
         * 같은 블록에 존재하는 다른 태그 유형들을 함께 전달하여 복합 reason 생성
         * @param blockTagTypes 같은 블록의 모든 태그 title prefix 집합 (예: "CONFUSED", "EXAM")
         */
        public static ReviewQueueItemDto from(ReviewQueueItem item, Set<String> blockTagTypes) {
            String subjectName = null;
            if (item.getDocument().getSubject() != null) {
                subjectName = item.getDocument().getSubject().getName();
            }

            String blockContent = null;
            if (item.getBlock() != null && item.getBlock().getContent() != null) {
                blockContent = item.getBlock().getContent().length() > 200
                        ? item.getBlock().getContent().substring(0, 200) + "..."
                        : item.getBlock().getContent();
            }

            String sourceDetail = buildSourceDetail(item);
            String studyLink = buildStudyLink(item);
            String reason = buildReason(item, blockTagTypes);

            return ReviewQueueItemDto.builder()
                    .id(item.getId())
                    .documentId(item.getDocument().getId())
                    .documentTitle(item.getDocument().getTitle())
                    .subjectName(subjectName)
                    .blockId(item.getBlock() != null ? item.getBlock().getId() : null)
                    .blockContent(blockContent)
                    .sourceType(item.getSourceType().name())
                    .sourceDetail(sourceDetail)
                    .title(item.getTitle())
                    .priority(item.getPriority())
                    .status(item.getStatus().name())
                    .deferCount(item.getDeferCount() != null ? item.getDeferCount() : 0)
                    .confusedCount(item.getConfusedCount() != null ? item.getConfusedCount() : 0)
                    .scheduledDate(item.getScheduledDate())
                    .createdAt(item.getCreatedAt())
                    .studyLink(studyLink)
                    .reason(reason)
                    .build();
        }

        private static String buildStudyLink(ReviewQueueItem item) {
            Long documentId = item.getDocument().getId();
            if (item.getBlock() != null) {
                return "/study/" + documentId + "?block=" + item.getBlock().getId();
            }
            return "/study/" + documentId;
        }

        /**
         * 추천 이유를 우선순위에 따라 결정한다.
         * @param blockTagTypes 같은 블록에 존재하는 모든 태그 유형 (title prefix 기준)
         */
        private static String buildReason(ReviewQueueItem item, Set<String> blockTagTypes) {
            int confusedCount = item.getConfusedCount() != null ? item.getConfusedCount() : 0;
            int deferCount = item.getDeferCount() != null ? item.getDeferCount() : 0;
            boolean isConfusedTag = item.getSourceType() == SourceType.TAG
                    && item.getTitle() != null && item.getTitle().startsWith("[CONFUSED]");
            boolean isExamTag = item.getSourceType() == SourceType.TAG
                    && item.getTitle() != null && item.getTitle().startsWith("[EXAM]");

            // 같은 블록에 CONFUSED + EXAM 태그가 함께 존재하는 경우
            boolean blockHasConfused = isConfusedTag || blockTagTypes.contains("CONFUSED");
            boolean blockHasExam = isExamTag || blockTagTypes.contains("EXAM");

            if (blockHasConfused && blockHasExam) {
                return "헷갈림과 시험 태그가 함께 찍혔습니다";
            }
            if (confusedCount >= 2) {
                return "반복적으로 헷갈리는 개념입니다";
            }
            if (deferCount >= 2) {
                return "계속 미루고 있는 항목입니다";
            }
            if (isConfusedTag) {
                return "헷갈림 태그가 찍혔습니다";
            }
            if (isExamTag) {
                return "시험 출제 가능성이 높습니다";
            }
            if (item.getSourceType() == SourceType.NOTE) {
                return "직접 메모를 남긴 부분입니다";
            }
            if (item.getLastActivityAt() != null
                    && item.getLastActivityAt().isAfter(LocalDateTime.now().minusDays(3))) {
                return "최근에 집중적으로 학습한 부분입니다";
            }
            return null;
        }

        private static String buildSourceDetail(ReviewQueueItem item) {
            return switch (item.getSourceType()) {
                case TAG -> item.getTitle() != null && item.getTitle().startsWith("[")
                        ? item.getTitle().substring(1, item.getTitle().indexOf("]")) + " 태그"
                        : "태그";
                case QUESTION -> "질문 " + item.getPriority() + "회";
                case NOTE -> "노트 메모";
            };
        }
    }
}
