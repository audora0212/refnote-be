package com.refnote.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
@Builder
public class ReviewQueueSummaryResponse {
    private long totalPending;
    private List<SubjectCount> bySubject;
    private Map<String, Long> bySource;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class SubjectCount {
        private Long subjectId;
        private String subjectName;
        private long count;
    }
}
