package com.refnote.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
    private Long id;
    private String content;
    private List<Long> relatedBlockIds;
    private List<Long> sourceExplanationIds;
    private String confidence;
}
