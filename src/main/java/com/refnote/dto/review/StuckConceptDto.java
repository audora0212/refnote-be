package com.refnote.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class StuckConceptDto {
    private Long blockId;
    private String blockContent;
    private String documentTitle;
    private String subjectName;
    private long actionCount;
    private List<Long> relatedItemIds;
}
