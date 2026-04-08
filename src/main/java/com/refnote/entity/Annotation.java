package com.refnote.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "annotations", indexes = {
    @Index(name = "idx_annotation_doc_page", columnList = "document_id, page_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private Integer pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnotationType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String data;

    @Column(length = 9)
    private String color;

    private Integer thickness;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum AnnotationType {
        PEN, HIGHLIGHTER, TEXT, SHAPE
    }
}
