-- Document 상태 enum 확장은 JPA @Enumerated(STRING)이므로 DDL 불필요

-- 분류 결과 컬럼 추가
ALTER TABLE documents ADD COLUMN estimated_subject VARCHAR(50) NULL;
ALTER TABLE documents ADD COLUMN estimated_difficulty VARCHAR(20) NULL;
ALTER TABLE documents ADD COLUMN document_type VARCHAR(30) NULL;
ALTER TABLE documents ADD COLUMN is_study_material BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE documents ADD COLUMN rejection_reason VARCHAR(500) NULL;
