package com.refnote.scheduler;

import com.refnote.entity.ReviewQueueItem;
import com.refnote.entity.ReviewStatus;
import com.refnote.repository.ReviewQueueItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 매일 자정에 DEFERRED/CONFUSED 항목 중 scheduledDate <= 오늘인 것을 PENDING으로 복귀시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewQueueScheduler {

    private final ReviewQueueItemRepository reviewQueueItemRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void reactivateDeferredItems() {
        List<ReviewQueueItem> items = reviewQueueItemRepository
                .findAllByStatusInAndScheduledDateLessThanEqual(
                        List.of(ReviewStatus.DEFERRED, ReviewStatus.CONFUSED),
                        LocalDate.now()
                );

        if (items.isEmpty()) {
            log.debug("재활성화 대상 항목 없음");
            return;
        }

        for (ReviewQueueItem item : items) {
            item.setStatus(ReviewStatus.PENDING);
        }
        reviewQueueItemRepository.saveAll(items);
        log.info("복습 큐 재활성화 완료 - {}건 PENDING으로 복귀", items.size());
    }
}
