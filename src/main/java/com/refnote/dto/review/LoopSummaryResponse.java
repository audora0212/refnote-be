package com.refnote.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class LoopSummaryResponse {
    private long completedToday;
    private long remainingConfused;
    private List<DeferredItem> frequentlyDeferred;
    private List<SubjectProgress> bySubject;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class DeferredItem {
        private Long id;
        private String title;
        private int deferCount;
        private String subjectName;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class SubjectProgress {
        private Long subjectId;
        private String subjectName;
        private long remaining;
        private long completed;
    }
}
