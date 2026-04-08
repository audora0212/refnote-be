package com.refnote.dto.subject;

import com.refnote.entity.Subject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class SubjectResponse {
    private Long id;
    private String name;
    private String color;
    private int documentCount;
    private LocalDateTime createdAt;

    public static SubjectResponse from(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .color(subject.getColor())
                .documentCount(subject.getDocuments().size())
                .createdAt(subject.getCreatedAt())
                .build();
    }

    public static SubjectResponse from(Subject subject, int documentCount) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .color(subject.getColor())
                .documentCount(documentCount)
                .createdAt(subject.getCreatedAt())
                .build();
    }
}
