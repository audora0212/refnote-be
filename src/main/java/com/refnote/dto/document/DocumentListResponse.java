package com.refnote.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class DocumentListResponse {
    private List<DocumentResponse> documents;
}
