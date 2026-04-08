package com.refnote.dto.session;

import com.refnote.entity.StudySession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class SessionResponse {
    private Long documentId;
    private Integer currentPage;
    private Double scrollPosition;
    private LocalDateTime lastActiveAt;

    public static SessionResponse from(StudySession session) {
        return SessionResponse.builder()
                .documentId(session.getDocument().getId())
                .currentPage(session.getCurrentPage())
                .scrollPosition(session.getScrollPosition())
                .lastActiveAt(session.getLastActiveAt())
                .build();
    }
}
