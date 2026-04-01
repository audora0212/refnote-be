package com.refnote.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pdf_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private Integer pageNumber;

    @Column(nullable = false)
    private Integer blockOrder;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BlockType blockType = BlockType.TEXT;

    private Double x;
    private Double y;
    private Double width;
    private Double height;

    public enum BlockType {
        TEXT, FORMULA, TABLE, IMAGE, HEADING
    }
}
