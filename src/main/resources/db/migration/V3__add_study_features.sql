-- 과목 폴더
CREATE TABLE subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_subject (user_id, name)
);

-- documents에 subject_id 추가
ALTER TABLE documents ADD COLUMN subject_id BIGINT NULL;
ALTER TABLE documents ADD CONSTRAINT fk_documents_subject
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL;

-- 학습 태그
CREATE TABLE study_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    block_id BIGINT NOT NULL,
    tag_type ENUM('IMPORTANT','EXAM','CONFUSED','REVIEW','MEMORIZE') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (block_id) REFERENCES pdf_blocks(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_block_tag (user_id, block_id, tag_type)
);

-- 사용자 노트
CREATE TABLE notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    block_id BIGINT,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (block_id) REFERENCES pdf_blocks(id) ON DELETE SET NULL
);

-- 자동 복습 큐
CREATE TABLE review_queue_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    block_id BIGINT,
    source_type ENUM('TAG','QUESTION','NOTE') NOT NULL,
    source_id BIGINT,
    title VARCHAR(500),
    status ENUM('PENDING','REVIEWED','DISMISSED') NOT NULL DEFAULT 'PENDING',
    priority INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (block_id) REFERENCES pdf_blocks(id) ON DELETE SET NULL
);

-- 학습 세션 (위치 동기화)
CREATE TABLE study_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    current_page INT NOT NULL DEFAULT 1,
    scroll_position DOUBLE NOT NULL DEFAULT 0,
    last_active_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_document_session (user_id, document_id)
);

-- 인덱스
CREATE INDEX idx_study_tags_user_doc ON study_tags(user_id, document_id);
CREATE INDEX idx_study_tags_block ON study_tags(block_id);
CREATE INDEX idx_notes_user_doc ON notes(user_id, document_id);
CREATE INDEX idx_notes_block ON notes(block_id);
CREATE INDEX idx_review_queue_user ON review_queue_items(user_id, status);
CREATE INDEX idx_review_queue_doc ON review_queue_items(document_id);
CREATE INDEX idx_study_sessions_user ON study_sessions(user_id);
