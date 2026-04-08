package com.refnote.repository;

import com.refnote.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class StudyTagRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private StudyTagRepository studyTagRepository;

    private User testUser;
    private Document testDocument;
    private PdfBlock block1;
    private PdfBlock block2;

    @BeforeEach
    void setUp() {
        testUser = em.persist(User.builder()
                .email("repo-test@test.com")
                .password("encoded")
                .nickname("tester")
                .build());

        testDocument = em.persist(Document.builder()
                .user(testUser)
                .title("테스트문서")
                .status(Document.DocumentStatus.READY)
                .build());

        block1 = em.persist(PdfBlock.builder()
                .document(testDocument).pageNumber(1).blockOrder(1).content("블록1").build());
        block2 = em.persist(PdfBlock.builder()
                .document(testDocument).pageNumber(1).blockOrder(2).content("블록2").build());

        em.persist(StudyTag.builder()
                .user(testUser).document(testDocument).block(block1).tagType(TagType.EXAM).build());
        em.persist(StudyTag.builder()
                .user(testUser).document(testDocument).block(block2).tagType(TagType.EXAM).build());
        em.persist(StudyTag.builder()
                .user(testUser).document(testDocument).block(block1).tagType(TagType.IMPORTANT).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("문서별 태그 요약 쿼리 - 타입별 count 정확성")
    void countByTagType_correctCounts() {
        List<Object[]> result = studyTagRepository.countByTagType(testUser.getId(), testDocument.getId());

        assertThat(result).isNotEmpty();

        long examCount = 0;
        long importantCount = 0;
        for (Object[] row : result) {
            TagType type = (TagType) row[0];
            Long count = (Long) row[1];
            if (type == TagType.EXAM) examCount = count;
            if (type == TagType.IMPORTANT) importantCount = count;
        }

        assertThat(examCount).isEqualTo(2);
        assertThat(importantCount).isEqualTo(1);
    }

    @Test
    @DisplayName("유저+블록+태그타입 중복 존재 여부 확인")
    void existsByUserIdAndBlockIdAndTagType() {
        assertThat(studyTagRepository.existsByUserIdAndBlockIdAndTagType(
                testUser.getId(), block1.getId(), TagType.EXAM)).isTrue();
        assertThat(studyTagRepository.existsByUserIdAndBlockIdAndTagType(
                testUser.getId(), block1.getId(), TagType.CONFUSED)).isFalse();
    }
}
