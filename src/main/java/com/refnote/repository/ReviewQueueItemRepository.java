package com.refnote.repository;

import com.refnote.entity.ReviewQueueItem;
import com.refnote.entity.ReviewStatus;
import com.refnote.entity.SourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewQueueItemRepository extends JpaRepository<ReviewQueueItem, Long> {

    Page<ReviewQueueItem> findAllByUserIdAndStatus(Long userId, ReviewStatus status, Pageable pageable);

    @Query("SELECT r FROM ReviewQueueItem r " +
            "JOIN r.document d " +
            "WHERE r.user.id = :userId AND r.status = :status " +
            "AND d.subject.id = :subjectId " +
            "ORDER BY r.priority DESC, r.createdAt DESC")
    Page<ReviewQueueItem> findAllByUserIdAndStatusAndSubjectId(
            @Param("userId") Long userId,
            @Param("status") ReviewStatus status,
            @Param("subjectId") Long subjectId,
            Pageable pageable);

    long countByUserIdAndStatus(Long userId, ReviewStatus status);

    Optional<ReviewQueueItem> findByUserIdAndSourceTypeAndSourceId(Long userId, SourceType sourceType, Long sourceId);

    Optional<ReviewQueueItem> findByUserIdAndBlockIdAndSourceType(Long userId, Long blockId, SourceType sourceType);

    @Query("SELECT d.subject.id, d.subject.name, COUNT(r) " +
            "FROM ReviewQueueItem r JOIN r.document d " +
            "WHERE r.user.id = :userId AND r.status = 'PENDING' " +
            "AND d.subject IS NOT NULL " +
            "GROUP BY d.subject.id, d.subject.name")
    List<Object[]> countPendingBySubject(@Param("userId") Long userId);

    @Query("SELECT r.sourceType, COUNT(r) " +
            "FROM ReviewQueueItem r " +
            "WHERE r.user.id = :userId AND r.status = 'PENDING' " +
            "GROUP BY r.sourceType")
    List<Object[]> countPendingBySourceType(@Param("userId") Long userId);

    // === 대시보드용 쿼리 (v3) ===

    /**
     * 오늘 볼 항목: PENDING/CONFUSED + scheduledDate <= 오늘
     */
    @Query("SELECT r FROM ReviewQueueItem r JOIN FETCH r.document d LEFT JOIN FETCH d.subject " +
           "WHERE r.user.id = :userId " +
           "AND r.status IN (com.refnote.entity.ReviewStatus.PENDING, com.refnote.entity.ReviewStatus.CONFUSED) " +
           "AND (r.scheduledDate IS NULL OR r.scheduledDate <= :today)")
    List<ReviewQueueItem> findTodayItems(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * 스케줄러: DEFERRED/CONFUSED 중 scheduledDate <= date 인 항목 재활성화 대상
     */
    List<ReviewQueueItem> findAllByStatusInAndScheduledDateLessThanEqual(
            List<ReviewStatus> statuses, LocalDate date);

    /**
     * 같은 블록에 2건 이상 존재하는 "가장 많이 막힌" 블록 감지
     */
    @Query("SELECT r.block.id, COUNT(r) FROM ReviewQueueItem r " +
           "WHERE r.user.id = :userId AND r.status IN (com.refnote.entity.ReviewStatus.PENDING, com.refnote.entity.ReviewStatus.CONFUSED) " +
           "AND r.block IS NOT NULL " +
           "GROUP BY r.block.id HAVING COUNT(r) >= 2")
    List<Object[]> findStuckBlocks(@Param("userId") Long userId);

    /**
     * 오늘 완료 수
     */
    @Query("SELECT COUNT(r) FROM ReviewQueueItem r " +
           "WHERE r.user.id = :userId AND r.status = com.refnote.entity.ReviewStatus.REVIEWED " +
           "AND r.reviewedAt >= :todayStart")
    long countCompletedToday(@Param("userId") Long userId, @Param("todayStart") LocalDateTime todayStart);

    /**
     * DEFERRED 2회 이상인 항목
     */
    List<ReviewQueueItem> findAllByUserIdAndDeferCountGreaterThanEqual(Long userId, int minCount);

    /**
     * 과목별 PENDING/CONFUSED 수 집계 (대시보드 bySubject)
     */
    @Query("SELECT d.subject.id, d.subject.name, " +
           "SUM(CASE WHEN r.status = com.refnote.entity.ReviewStatus.PENDING THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN r.status = com.refnote.entity.ReviewStatus.CONFUSED THEN 1 ELSE 0 END) " +
           "FROM ReviewQueueItem r JOIN r.document d " +
           "WHERE r.user.id = :userId AND r.status IN (com.refnote.entity.ReviewStatus.PENDING, com.refnote.entity.ReviewStatus.CONFUSED) " +
           "AND d.subject IS NOT NULL " +
           "GROUP BY d.subject.id, d.subject.name")
    List<Object[]> countPendingAndConfusedBySubject(@Param("userId") Long userId);

    /**
     * 특정 블록의 PENDING/CONFUSED 항목 조회 (mostStuck 상세)
     */
    List<ReviewQueueItem> findAllByUserIdAndBlockIdAndStatusIn(Long userId, Long blockId, List<ReviewStatus> statuses);

    /**
     * 과목별 오늘 완료 수 집계 (loopSummary bySubject)
     */
    @Query("SELECT d.subject.id, d.subject.name, " +
           "SUM(CASE WHEN r.status = com.refnote.entity.ReviewStatus.REVIEWED AND r.reviewedAt >= :todayStart THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN r.status IN (com.refnote.entity.ReviewStatus.PENDING, com.refnote.entity.ReviewStatus.CONFUSED) THEN 1 ELSE 0 END) " +
           "FROM ReviewQueueItem r JOIN r.document d " +
           "WHERE r.user.id = :userId AND d.subject IS NOT NULL " +
           "GROUP BY d.subject.id, d.subject.name")
    List<Object[]> countBySubjectWithCompleted(@Param("userId") Long userId, @Param("todayStart") LocalDateTime todayStart);

    void deleteAllByDocumentId(Long documentId);
}
