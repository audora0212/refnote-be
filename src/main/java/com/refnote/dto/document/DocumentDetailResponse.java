package com.refnote.dto.document;

import com.refnote.dto.explanation.ExplanationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class DocumentDetailResponse {
    private Long id;
    private String title;
    private String s3Url;
    private Integer pageCount;
    private String status;
    private Long subjectId;
    private String subjectName;
    private List<PdfBlockResponse> blocks;
    private List<ExplanationResponse> explanations;
    private LocalDateTime createdAt;
}
