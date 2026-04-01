package com.refnote.dto.chat;

import com.refnote.entity.ChatMessage;
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
public class ChatHistoryResponse {
    private List<ChatMessageDto> messages;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class ChatMessageDto {
        private Long id;
        private String role;
        private String content;
        private List<Long> relatedBlockIds;
        private LocalDateTime createdAt;

        public static ChatMessageDto from(ChatMessage msg) {
            List<Long> blockIds = Collections.emptyList();
            if (msg.getRelatedBlockIds() != null && !msg.getRelatedBlockIds().isBlank()) {
                blockIds = Arrays.stream(msg.getRelatedBlockIds().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
            }

            return ChatMessageDto.builder()
                    .id(msg.getId())
                    .role(msg.getRole().name())
                    .content(msg.getContent())
                    .relatedBlockIds(blockIds)
                    .createdAt(msg.getCreatedAt())
                    .build();
        }
    }
}
