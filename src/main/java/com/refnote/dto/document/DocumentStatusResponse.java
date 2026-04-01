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
}
