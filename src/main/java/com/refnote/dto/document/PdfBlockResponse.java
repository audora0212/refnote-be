package com.refnote.dto.document;

import com.refnote.entity.PdfBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class PdfBlockResponse {
    private Long id;
    private Integer pageNumber;
    private Integer blockOrder;
    private String content;
    private String blockType;
    private Double x;
    private Double y;
    private Double width;
    private Double height;

    public static PdfBlockResponse from(PdfBlock block) {
        return PdfBlockResponse.builder()
                .id(block.getId())
                .pageNumber(block.getPageNumber())
                .blockOrder(block.getBlockOrder())
                .content(block.getContent())
                .blockType(block.getBlockType().name())
                .x(block.getX())
                .y(block.getY())
                .width(block.getWidth())
                .height(block.getHeight())
                .build();
    }
}
