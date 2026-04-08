package com.refnote.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class DocumentStatusResponse {
    private String status;
    private Integer progress;

    // 분류 결과 필드 (v2)
    private Boolean isStudyMaterial;
    private String estimatedSubject;
    private String estimatedDifficulty;
    private String documentType;
    private String rejectionReason;
}
