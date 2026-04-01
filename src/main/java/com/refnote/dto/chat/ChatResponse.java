package com.refnote.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class ChatResponse {
    private Long id;
    private String content;
    private List<Long> relatedBlockIds;
}
