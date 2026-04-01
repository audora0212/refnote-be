package com.refnote.dto.document;

import com.refnote.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class DocumentResponse {
    private Long id;
    private String title;
    private String s3Url;
    private Integer pageCount;
    private String status;
    private LocalDateTime createdAt;

    public static DocumentResponse from(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .s3Url(doc.getS3Url())
                .pageCount(doc.getPageCount())
                .status(doc.getStatus().name())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
