package com.refnote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_document_session",
                columnNames = {"user_id", "document_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudySession {

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
    @Builder.Default
    private Integer currentPage = 1;

    @Column(nullable = false)
    @Builder.Default
    private Double scrollPosition = 0.0;

    @Column(nullable = false)
    private LocalDateTime lastActiveAt;

    @PrePersist
    @PreUpdate
    public void updateLastActiveAt() {
        this.lastActiveAt = LocalDateTime.now();
    }
}
