package com.refnote.dto.studytag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
@Builder
public class TagSummaryResponse {
    private Map<String, Long> summary;
}
