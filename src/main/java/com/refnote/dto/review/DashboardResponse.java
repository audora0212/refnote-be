package com.refnote.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class DashboardResponse {
    private Summary summary;
    private List<ReviewQueueResponse.ReviewQueueItemDto> todayItems;
    private List<ReviewQueueResponse.ReviewQueueItemDto> examEssentials;
    private List<StuckConceptDto> mostStuck;
    private List<SubjectStatus> bySubject;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private int todayReviewCount;
        private int examEssentialCount;
        private int mostStuckCount;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class SubjectStatus {
        private Long subjectId;
        private String subjectName;
        private long pendingCount;
        private long confusedCount;
    }
}
