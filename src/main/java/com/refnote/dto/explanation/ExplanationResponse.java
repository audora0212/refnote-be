package com.refnote.dto.explanation;

import com.refnote.entity.Explanation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@Builder
public class ExplanationResponse {
    private Long id;
    private List<Long> blockIds;
    private Integer explanationOrder;
    private String content;
    private String tag;
    private LocalDateTime createdAt;
    private LocalDateTime regeneratedAt;

    public static ExplanationResponse from(Explanation explanation) {
        List<Long> blockIdList = Collections.emptyList();
        if (explanation.getBlockIds() != null && !explanation.getBlockIds().isBlank()) {
            blockIdList = Arrays.stream(explanation.getBlockIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }

        return ExplanationResponse.builder()
                .id(explanation.getId())
                .blockIds(blockIdList)
                .explanationOrder(explanation.getExplanationOrder())
                .content(explanation.getContent())
                .tag(explanation.getTag().name())
                .createdAt(explanation.getCreatedAt())
                .regeneratedAt(explanation.getRegeneratedAt())
                .build();
    }
}
