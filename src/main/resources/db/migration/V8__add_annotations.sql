CREATE TABLE annotations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    document_id BIGINT       NOT NULL,
    page_number INT          NOT NULL,
    type        ENUM('PEN','HIGHLIGHTER','TEXT','SHAPE') NOT NULL,
    data        TEXT         NOT NULL,
    color       VARCHAR(9),
    thickness   INT,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_annotation_user     FOREIGN KEY (user_id)     REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_annotation_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    INDEX idx_annotation_doc_page (document_id, page_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
