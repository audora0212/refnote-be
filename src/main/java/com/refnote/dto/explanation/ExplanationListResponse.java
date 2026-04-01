package com.refnote.dto.explanation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class ExplanationListResponse {
    private List<ExplanationResponse> explanations;
}
