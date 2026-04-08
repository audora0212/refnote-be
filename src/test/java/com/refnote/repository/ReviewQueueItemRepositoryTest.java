package com.refnote.repository;

import com.refnote.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewQueueItemRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ReviewQueueItemRepository reviewQueueItemRepository;

    private User testUser;
    private Document doc1;
    private Document doc2;
    private Subject subject1;

    @BeforeEach
    void setUp() {
        testUser = em.persist(User.builder()
                .email("rq-test@test.com")
                .password("encoded")
                .nickname("tester")
                .build());

        subject1 = em.persist(Subject.builder()
                .user(testUser).name("운영체제").color("#e8a830").build());

        doc1 = em.persist(Document.builder()
                .user(testUser).title("OS 3장").subject(subject1)
                .status(Document.DocumentStatus.READY).build());
        doc2 = em.persist(Document.builder()
                .user(testUser).title("DB 1장")
                .status(Document.DocumentStatus.READY).build());

        PdfBlock block = em.persist(PdfBlock.builder()
                .document(doc1).pageNumber(1).blockOrder(1).content("블록").build());

        em.persist(ReviewQueueItem.builder()
                .user(testUser).document(doc1).block(block)
                .sourceType(SourceType.TAG).sourceId(1L)
                .title("[CONFUSED] 블록").priority(5).build());

        em.persist(ReviewQueueItem.builder()
                .user(testUser).document(doc1).block(block)
                .sourceType(SourceType.NOTE).sourceId(2L)
                .title("노트").priority(1).build());

        em.persist(ReviewQueueItem.builder()
                .user(testUser).document(doc2)
                .sourceType(SourceType.QUESTION).sourceId(3L)
                .title("질문").priority(3)
                .status(ReviewStatus.REVIEWED).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("상태별 필터 쿼리 - PENDING만 조회")
    void findByStatus_pending() {
        Page<ReviewQueueItem> result = reviewQueueItemRepository
                .findAllByUserIdAndStatus(testUser.getId(), ReviewStatus.PENDING, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("상태별 필터 쿼리 - REVIEWED만 조회")
    void findByStatus_reviewed() {
        Page<ReviewQueueItem> result = reviewQueueItemRepository
                .findAllByUserIdAndStatus(testUser.getId(), ReviewStatus.REVIEWED, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("과목별 필터 쿼리 - subject1만 조회")
    void findBySubject() {
        Page<ReviewQueueItem> result = reviewQueueItemRepository
                .findAllByUserIdAndStatusAndSubjectId(
                        testUser.getId(), ReviewStatus.PENDING, subject1.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("요약 집계 쿼리 - 소스타입별 카운트")
    void countPendingBySourceType() {
        List<Object[]> result = reviewQueueItemRepository.countPendingBySourceType(testUser.getId());

        assertThat(result).isNotEmpty();
        long tagCount = 0;
        long noteCount = 0;
        for (Object[] row : result) {
            SourceType type = (SourceType) row[0];
            Long count = (Long) row[1];
            if (type == SourceType.TAG) tagCount = count;
            if (type == SourceType.NOTE) noteCount = count;
        }
        assertThat(tagCount).isEqualTo(1);
        assertThat(noteCount).isEqualTo(1);
    }

    @Test
    @DisplayName("요약 집계 쿼리 - 과목별 카운트")
    void countPendingBySubject() {
        List<Object[]> result = reviewQueueItemRepository.countPendingBySubject(testUser.getId());

        assertThat(result).hasSize(1);
        assertThat((String) result.get(0)[1]).isEqualTo("운영체제");
        assertThat((Long) result.get(0)[2]).isEqualTo(2);
    }
}
