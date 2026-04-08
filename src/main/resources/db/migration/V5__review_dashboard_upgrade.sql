ALTER TABLE review_queue_items ADD COLUMN defer_count INT NOT NULL DEFAULT 0;
ALTER TABLE review_queue_items ADD COLUMN confused_count INT NOT NULL DEFAULT 0;
ALTER TABLE review_queue_items ADD COLUMN last_activity_at DATETIME;
ALTER TABLE review_queue_items ADD COLUMN scheduled_date DATE;

-- 기존 PENDING 항목에 scheduled_date를 오늘로 설정
UPDATE review_queue_items SET scheduled_date = CURDATE(), last_activity_at = created_at WHERE status = 'PENDING';
