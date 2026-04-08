package com.refnote.service;

import com.refnote.dto.review.*;
import com.refnote.entity.*;
import com.refnote.exception.ApiException;
import com.refnote.repository.ChatMessageRepository;
import com.refnote.repository.ExplanationRepository;
import com.refnote.repository.PdfBlockRepository;
import com.refnote.repository.ReviewQueueItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private final ReviewQueueItemRepository reviewQueueItemRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PdfBlockRepository pdfBlockRepository;
    private final ExplanationRepository explanationRepository;
    private final ClaudeApiService claudeApiService;

    @Transactional
    public void onTagCreated(StudyTag tag) {
        int priority = calculateTagPriority(tag.getTagType());
        String title = buildTagTitle(tag);

        createOrUpdateQueueItem(
                tag.getUser(),
                tag.getDocument(),
                tag.getBlock(),
                SourceType.TAG,
                tag.getId(),
                title,
                priority
        );
        log.debug("복습 큐 추가 (태그) - tagId: {}, priority: {}", tag.getId(), priority);
    }

    @Transactional
    public void onQuestionAsked(ChatMessage message) {
        if (message.getRelatedBlockIds() == null || message.getRelatedBlockIds().isBlank()) {
            return;
        }

        long questionCount = chatMessageRepository.countByDocumentIdAndUserIdAndRole(
                message.getDocument().getId(),
                message.getUser().getId(),
                ChatMessage.MessageRole.USER
        );

        if (questionCount < 2) {
            return;
        }

        String title = truncate(message.getContent(), 100);

        createOrUpdateQueueItem(
                message.getUser(),
                message.getDocument(),
                null,
                SourceType.QUESTION,
                message.getId(),
                title,
                (int) questionCount
        );
        log.debug("복습 큐 추가 (질문) - messageId: {}, questionCount: {}", message.getId(), questionCount);
    }

    @Transactional
    public void onNoteCreated(Note note) {
        if (note.getBlock() == null) {
            return;
        }

        String title = truncate(note.getContent(), 100);

        createOrUpdateQueueItem(
                note.getUser(),
                note.getDocument(),
                note.getBlock(),
                SourceType.NOTE,
                note.getId(),
                title,
                1
        );
        log.debug("복습 큐 추가 (노트) - noteId: {}", note.getId());
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse getReviewQueue(Long userId, Long subjectId, ReviewStatus status, int page, int size) {
        if (status == null) {
            status = ReviewStatus.PENDING;
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "priority", "createdAt"));

        Page<ReviewQueueItem> itemPage;
        if (subjectId != null) {
            itemPage = reviewQueueItemRepository.findAllByUserIdAndStatusAndSubjectId(userId, status, subjectId, pageRequest);
        } else {
            itemPage = reviewQueueItemRepository.findAllByUserIdAndStatus(userId, status, pageRequest);
        }

        long totalCount = itemPage.getTotalElements();
        long pendingCount = reviewQueueItemRepository.countByUserIdAndStatus(userId, ReviewStatus.PENDING);

        Map<Long, Set<String>> blockTagTypeMap = buildBlockTagTypeMap(itemPage.getContent());

        List<ReviewQueueResponse.ReviewQueueItemDto> items = itemPage.getContent().stream()
                .map(item -> ReviewQueueResponse.ReviewQueueItemDto.from(
                        item, getBlockTagTypes(item, blockTagTypeMap)))
                .collect(Collectors.toList());

        return ReviewQueueResponse.builder()
                .items(items)
                .totalCount(totalCount)
                .pendingCount(pendingCount)
                .build();
    }

    @Transactional(readOnly = true)
    public ReviewQueueSummaryResponse getSummary(Long userId) {
        long totalPending = reviewQueueItemRepository.countByUserIdAndStatus(userId, ReviewStatus.PENDING);

        List<Object[]> bySubject = reviewQueueItemRepository.countPendingBySubject(userId);
        List<ReviewQueueSummaryResponse.SubjectCount> subjectCounts = bySubject.stream()
                .map(row -> ReviewQueueSummaryResponse.SubjectCount.builder()
                        .subjectId((Long) row[0])
                        .subjectName((String) row[1])
                        .count((Long) row[2])
                        .build())
                .collect(Collectors.toList());

        List<Object[]> bySource = reviewQueueItemRepository.countPendingBySourceType(userId);
        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        sourceCounts.put("TAG", 0L);
        sourceCounts.put("QUESTION", 0L);
        sourceCounts.put("NOTE", 0L);
        for (Object[] row : bySource) {
            sourceCounts.put(((SourceType) row[0]).name(), (Long) row[1]);
        }

        return ReviewQueueSummaryResponse.builder()
                .totalPending(totalPending)
                .bySubject(subjectCounts)
                .bySource(sourceCounts)
                .build();
    }

    @Transactional
    public void markReviewed(Long itemId, Long userId) {
        ReviewQueueItem item = findItemAndVerifyOwner(itemId, userId);
        item.setStatus(ReviewStatus.REVIEWED);
        item.setReviewedAt(LocalDateTime.now());
        reviewQueueItemRepository.save(item);
        log.info("복습 완료 처리 - itemId: {}", itemId);
    }

    @Transactional
    public void dismiss(Long itemId, Long userId) {
        ReviewQueueItem item = findItemAndVerifyOwner(itemId, userId);
        item.setStatus(ReviewStatus.DISMISSED);
        item.setReviewedAt(LocalDateTime.now());
        reviewQueueItemRepository.save(item);
        log.info("복습 항목 건너뛰기 - itemId: {}", itemId);
    }

    // === v3: 대시보드 + 학습 루프 ===

    /**
     * 동적 우선순위 재산정 (조회 시점에 호출)
     */
    int recalculatePriority(ReviewQueueItem item) {
        int score = 0;

        // 태그 기반 점수
        if (item.getSourceType() == SourceType.TAG) {
            score += item.getPriority(); // 기존 정적 점수 활용
        }

        // 시간 감쇠: 최근 3일 이내 활동 +2
        if (item.getLastActivityAt() != null &&
                item.getLastActivityAt().isAfter(LocalDateTime.now().minusDays(3))) {
            score += 2;
        }

        // CONFUSED 누적
        if (item.getConfusedCount() != null) {
            score += item.getConfusedCount() * 2;
        }

        // DEFERRED 2회 이상
        if (item.getDeferCount() != null && item.getDeferCount() >= 2) {
            score += 1;
        }

        // 노트 존재
        if (item.getSourceType() == SourceType.NOTE) {
            score += 2;
        }

        return score;
    }

    /**
     * 시험 대비 대시보드 조회
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        LocalDate today = LocalDate.now();

        // 1. 오늘 볼 항목
        List<ReviewQueueItem> todayRawItems = reviewQueueItemRepository.findTodayItems(userId, today);

        // 동적 우선순위 재산정 후 정렬
        todayRawItems.forEach(item -> item.setPriority(recalculatePriority(item)));
        todayRawItems.sort(Comparator.comparingInt(ReviewQueueItem::getPriority).reversed());

        Map<Long, Set<String>> blockTagTypeMap = buildBlockTagTypeMap(todayRawItems);

        List<ReviewQueueResponse.ReviewQueueItemDto> todayItems = todayRawItems.stream()
                .limit(10)
                .map(item -> ReviewQueueResponse.ReviewQueueItemDto.from(
                        item, getBlockTagTypes(item, blockTagTypeMap)))
                .collect(Collectors.toList());

        // 2. 시험 전 필수 항목: EXAM 태그 OR confusedCount >= 2
        List<ReviewQueueResponse.ReviewQueueItemDto> examEssentials = todayRawItems.stream()
                .filter(item -> {
                    boolean isExamTag = item.getSourceType() == SourceType.TAG &&
                            item.getTitle() != null && item.getTitle().startsWith("[EXAM]");
                    boolean isConfusedTag = item.getSourceType() == SourceType.TAG &&
                            item.getTitle() != null && item.getTitle().startsWith("[CONFUSED]");
                    boolean highConfused = item.getConfusedCount() != null && item.getConfusedCount() >= 2;
                    return isExamTag || isConfusedTag || highConfused;
                })
                .map(item -> ReviewQueueResponse.ReviewQueueItemDto.from(
                        item, getBlockTagTypes(item, blockTagTypeMap)))
                .collect(Collectors.toList());

        // 3. 가장 많이 막힌 개념 (같은 block에 2건 이상)
        List<Object[]> stuckBlocks = reviewQueueItemRepository.findStuckBlocks(userId);
        List<ReviewStatus> activeStatuses = List.of(ReviewStatus.PENDING, ReviewStatus.CONFUSED);
        List<StuckConceptDto> mostStuck = stuckBlocks.stream()
                .map(row -> {
                    Long blockId = (Long) row[0];
                    Long count = (Long) row[1];
                    List<ReviewQueueItem> relatedItems = reviewQueueItemRepository
                            .findAllByUserIdAndBlockIdAndStatusIn(userId, blockId, activeStatuses);
                    PdfBlock block = relatedItems.isEmpty() ? null :
                            relatedItems.get(0).getBlock();

                    String blockContent = block != null ? truncate(block.getContent(), 200) : "";
                    String documentTitle = relatedItems.isEmpty() ? "" :
                            relatedItems.get(0).getDocument().getTitle();
                    String subjectName = "";
                    if (!relatedItems.isEmpty() && relatedItems.get(0).getDocument().getSubject() != null) {
                        subjectName = relatedItems.get(0).getDocument().getSubject().getName();
                    }

                    return StuckConceptDto.builder()
                            .blockId(blockId)
                            .blockContent(blockContent)
                            .documentTitle(documentTitle)
                            .subjectName(subjectName)
                            .actionCount(count)
                            .relatedItemIds(relatedItems.stream().map(ReviewQueueItem::getId).collect(Collectors.toList()))
                            .build();
                })
                .sorted(Comparator.comparingLong(StuckConceptDto::getActionCount).reversed())
                .collect(Collectors.toList());

        // 4. 과목별 현황
        List<Object[]> subjectData = reviewQueueItemRepository.countPendingAndConfusedBySubject(userId);
        List<DashboardResponse.SubjectStatus> bySubject = subjectData.stream()
                .map(row -> DashboardResponse.SubjectStatus.builder()
                        .subjectId((Long) row[0])
                        .subjectName((String) row[1])
                        .pendingCount((Long) row[2])
                        .confusedCount((Long) row[3])
                        .build())
                .collect(Collectors.toList());

        // Summary
        DashboardResponse.Summary summary = DashboardResponse.Summary.builder()
                .todayReviewCount(todayRawItems.size())
                .examEssentialCount(examEssentials.size())
                .mostStuckCount(mostStuck.size())
                .build();

        return DashboardResponse.builder()
                .summary(summary)
                .todayItems(todayItems)
                .examEssentials(examEssentials)
                .mostStuck(mostStuck)
                .bySubject(bySubject)
                .build();
    }

    /**
     * 학습 루프 요약 조회
     */
    @Transactional(readOnly = true)
    public LoopSummaryResponse getLoopSummary(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 오늘 완료 수
        long completedToday = reviewQueueItemRepository.countCompletedToday(userId, todayStart);

        // 남은 CONFUSED 수
        long remainingConfused = reviewQueueItemRepository.countByUserIdAndStatus(userId, ReviewStatus.CONFUSED);

        // 반복 미룬 항목 (deferCount >= 2)
        List<ReviewQueueItem> deferredItems = reviewQueueItemRepository
                .findAllByUserIdAndDeferCountGreaterThanEqual(userId, 2);
        List<LoopSummaryResponse.DeferredItem> frequentlyDeferred = deferredItems.stream()
                .map(item -> {
                    String subjectName = "";
                    if (item.getDocument().getSubject() != null) {
                        subjectName = item.getDocument().getSubject().getName();
                    }
                    return LoopSummaryResponse.DeferredItem.builder()
                            .id(item.getId())
                            .title(item.getTitle())
                            .deferCount(item.getDeferCount() != null ? item.getDeferCount() : 0)
                            .subjectName(subjectName)
                            .build();
                })
                .collect(Collectors.toList());

        // 과목별 진행 상황
        List<Object[]> subjectData = reviewQueueItemRepository.countBySubjectWithCompleted(userId, todayStart);
        List<LoopSummaryResponse.SubjectProgress> bySubject = subjectData.stream()
                .map(row -> LoopSummaryResponse.SubjectProgress.builder()
                        .subjectId((Long) row[0])
                        .subjectName((String) row[1])
                        .completed((Long) row[2])
                        .remaining((Long) row[3])
                        .build())
                .collect(Collectors.toList());

        return LoopSummaryResponse.builder()
                .completedToday(completedToday)
                .remainingConfused(remainingConfused)
                .frequentlyDeferred(frequentlyDeferred)
                .bySubject(bySubject)
                .build();
    }

    /**
     * "아직 헷갈림" 처리
     */
    @Transactional
    public void markConfused(Long itemId, Long userId) {
        ReviewQueueItem item = findItemAndVerifyOwner(itemId, userId);
        item.setStatus(ReviewStatus.CONFUSED);
        item.setConfusedCount((item.getConfusedCount() != null ? item.getConfusedCount() : 0) + 1);
        item.setPriority(item.getPriority() + 3);
        item.setScheduledDate(LocalDate.now().plusDays(1));
        item.setLastActivityAt(LocalDateTime.now());
        item.setReviewedAt(LocalDateTime.now());
        reviewQueueItemRepository.save(item);
        log.info("헷갈림 처리 - itemId: {}, confusedCount: {}", itemId, item.getConfusedCount());
    }

    /**
     * "내일 다시" 처리
     */
    @Transactional
    public void defer(Long itemId, Long userId) {
        ReviewQueueItem item = findItemAndVerifyOwner(itemId, userId);
        item.setStatus(ReviewStatus.DEFERRED);
        item.setDeferCount((item.getDeferCount() != null ? item.getDeferCount() : 0) + 1);
        item.setScheduledDate(LocalDate.now().plusDays(1));
        item.setLastActivityAt(LocalDateTime.now());
        item.setReviewedAt(LocalDateTime.now());
        reviewQueueItemRepository.save(item);
        log.info("내일 다시 처리 - itemId: {}, deferCount: {}", itemId, item.getDeferCount());
    }

    /**
     * 복습 항목에 대한 미니 퀴즈를 생성한다.
     * 블록 원문 + 관련 해설을 기반으로 Claude API를 호출하여 OX 또는 빈칸 문제를 만든다.
     */
    @Transactional(readOnly = true)
    public QuizResponse generateQuiz(Long itemId, Long userId) {
        ReviewQueueItem item = findItemAndVerifyOwner(itemId, userId);

        // 블록 원문 가져오기
        String blockContent = "";
        Long sourceBlockId = null;
        if (item.getBlock() != null) {
            blockContent = item.getBlock().getContent() != null ? item.getBlock().getContent() : "";
            sourceBlockId = item.getBlock().getId();
        } else {
            // 블록이 없으면 title을 사용
            blockContent = item.getTitle() != null ? item.getTitle() : "";
        }

        // 관련 해설 가져오기
        String explanationContent = findRelatedExplanation(item);

        return claudeApiService.generateQuiz(blockContent, explanationContent, sourceBlockId);
    }

    /**
     * 복습 항목과 관련된 해설 내용을 찾는다.
     * 블록 ID를 기반으로 해당 블록이 포함된 해설을 탐색한다.
     */
    private String findRelatedExplanation(ReviewQueueItem item) {
        if (item.getBlock() == null || item.getDocument() == null) {
            return null;
        }

        Long blockId = item.getBlock().getId();
        Long documentId = item.getDocument().getId();
        String blockIdStr = String.valueOf(blockId);

        List<Explanation> explanations = explanationRepository
                .findByDocumentIdOrderByExplanationOrderAsc(documentId);

        for (Explanation explanation : explanations) {
            if (explanation.getBlockIds() != null) {
                String[] ids = explanation.getBlockIds().split(",");
                for (String id : ids) {
                    if (id.trim().equals(blockIdStr)) {
                        return explanation.getContent();
                    }
                }
            }
        }
        return null;
    }

    private void createOrUpdateQueueItem(User user, Document document, PdfBlock block,
                                          SourceType sourceType, Long sourceId,
                                          String title, int priority) {
        Optional<ReviewQueueItem> existing = reviewQueueItemRepository
                .findByUserIdAndSourceTypeAndSourceId(user.getId(), sourceType, sourceId);

        if (existing.isPresent()) {
            ReviewQueueItem item = existing.get();
            item.setPriority(Math.max(item.getPriority(), priority));
            item.setTitle(title);
            reviewQueueItemRepository.save(item);
        } else {
            ReviewQueueItem item = ReviewQueueItem.builder()
                    .user(user)
                    .document(document)
                    .block(block)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .title(title)
                    .priority(priority)
                    .build();
            reviewQueueItemRepository.save(item);
        }
    }

    int calculateTagPriority(TagType tagType) {
        return switch (tagType) {
            case CONFUSED -> 5;
            case EXAM -> 4;
            case IMPORTANT -> 3;
            case MEMORIZE -> 2;
            case REVIEW -> 1;
        };
    }

    private String buildTagTitle(StudyTag tag) {
        String blockContent = tag.getBlock().getContent();
        String prefix = "[" + tag.getTagType().name() + "] ";
        return prefix + truncate(blockContent, 100);
    }

    private ReviewQueueItem findItemAndVerifyOwner(Long itemId, Long userId) {
        ReviewQueueItem item = reviewQueueItemRepository.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("복습 항목을 찾을 수 없습니다."));
        if (!item.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("해당 복습 항목에 대한 접근 권한이 없습니다.");
        }
        return item;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * 같은 블록에 존재하는 태그 유형들을 매핑한다.
     * blockId -> Set<"CONFUSED", "EXAM", ...> 형태
     */
    private Map<Long, Set<String>> buildBlockTagTypeMap(List<ReviewQueueItem> items) {
        Map<Long, Set<String>> map = new HashMap<>();
        for (ReviewQueueItem item : items) {
            if (item.getBlock() == null || item.getSourceType() != SourceType.TAG) continue;
            Long blockId = item.getBlock().getId();
            String tagType = extractTagTypeFromTitle(item.getTitle());
            if (tagType != null) {
                map.computeIfAbsent(blockId, k -> new HashSet<>()).add(tagType);
            }
        }
        return map;
    }

    /**
     * 항목의 블록에 대한 태그 유형 집합을 반환한다.
     */
    private Set<String> getBlockTagTypes(ReviewQueueItem item, Map<Long, Set<String>> blockTagTypeMap) {
        if (item.getBlock() == null) return Set.of();
        return blockTagTypeMap.getOrDefault(item.getBlock().getId(), Set.of());
    }

    /**
     * "[CONFUSED] ..." 형식의 title에서 태그 유형을 추출한다.
     */
    private String extractTagTypeFromTitle(String title) {
        if (title != null && title.startsWith("[") && title.contains("]")) {
            return title.substring(1, title.indexOf("]"));
        }
        return null;
    }
}
