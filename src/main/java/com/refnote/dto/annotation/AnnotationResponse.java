package com.refnote.dto.annotation;

import com.refnote.entity.Annotation;
import com.refnote.entity.Annotation.AnnotationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class AnnotationResponse {

    private Long id;
    private Integer pageNumber;
    private AnnotationType type;
    private String data;
    private String color;
    private Integer thickness;
    private LocalDateTime createdAt;

    public static AnnotationResponse from(Annotation annotation) {
        return AnnotationResponse.builder()
                .id(annotation.getId())
                .pageNumber(annotation.getPageNumber())
                .type(annotation.getType())
                .data(annotation.getData())
                .color(annotation.getColor())
                .thickness(annotation.getThickness())
                .createdAt(annotation.getCreatedAt())
                .build();
    }
}
