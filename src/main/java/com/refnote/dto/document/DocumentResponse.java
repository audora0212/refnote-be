package com.refnote.dto.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.refnote.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {
    private Long id;
    private String title;
    private String s3Url;
    private Integer pageCount;
    private String status;
    private Long subjectId;
    private String subjectName;
    private Integer tagCount;
    private Integer noteCount;
    private LocalDateTime createdAt;

    public static DocumentResponse from(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .s3Url(doc.getS3Url())
                .pageCount(doc.getPageCount())
                .status(doc.getStatus().name())
                .subjectId(doc.getSubject() != null ? doc.getSubject().getId() : null)
                .subjectName(doc.getSubject() != null ? doc.getSubject().getName() : null)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    public static DocumentResponse from(Document doc, int tagCount, int noteCount) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .s3Url(doc.getS3Url())
                .pageCount(doc.getPageCount())
                .status(doc.getStatus().name())
                .subjectId(doc.getSubject() != null ? doc.getSubject().getId() : null)
                .subjectName(doc.getSubject() != null ? doc.getSubject().getName() : null)
                .tagCount(tagCount)
                .noteCount(noteCount)
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
