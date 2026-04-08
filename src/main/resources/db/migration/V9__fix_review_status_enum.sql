-- review_queue_items.status: COMPLETED → REVIEWED (Java ReviewStatus enum과 일치)
ALTER TABLE review_queue_items MODIFY COLUMN status ENUM('PENDING','REVIEWED','DISMISSED','DEFERRED','CONFUSED') NOT NULL DEFAULT 'PENDING';

-- 기존 COMPLETED 데이터가 있으면 REVIEWED로 변환
UPDATE review_queue_items SET status = 'REVIEWED' WHERE status = 'COMPLETED';
