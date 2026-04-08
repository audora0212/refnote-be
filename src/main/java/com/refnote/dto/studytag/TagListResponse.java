package com.refnote.dto.studytag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
@Builder
public class TagListResponse {
    private List<TagResponse> tags;
    private Map<String, Long> summary;
}
