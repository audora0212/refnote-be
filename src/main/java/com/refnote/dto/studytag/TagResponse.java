package com.refnote.dto.studytag;

import com.refnote.entity.StudyTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class TagResponse {
    private Long id;
    private Long blockId;
    private String tagType;
    private String blockContent;
    private Integer pageNumber;
    private LocalDateTime createdAt;

    public static TagResponse from(StudyTag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .blockId(tag.getBlock().getId())
                .tagType(tag.getTagType().name())
                .blockContent(truncate(tag.getBlock().getContent(), 200))
                .pageNumber(tag.getBlock().getPageNumber())
                .createdAt(tag.getCreatedAt())
                .build();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
