CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role ENUM('FREE', 'PRO') NOT NULL DEFAULT 'FREE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    s3_key VARCHAR(500),
    s3_url VARCHAR(1000),
    page_count INT,
    status ENUM('UPLOADING', 'PARSING', 'GENERATING', 'READY', 'FAILED') NOT NULL DEFAULT 'UPLOADING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE pdf_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    page_number INT NOT NULL,
    block_order INT NOT NULL,
    content TEXT,
    block_type ENUM('TEXT', 'FORMULA', 'TABLE', 'IMAGE', 'HEADING') NOT NULL DEFAULT 'TEXT',
    x DOUBLE,
    y DOUBLE,
    width DOUBLE,
    height DOUBLE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

CREATE TABLE explanations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    block_ids VARCHAR(500),
    explanation_order INT NOT NULL,
    content TEXT NOT NULL,
    tag ENUM('DEFINITION', 'KEY_CONCEPT', 'EXAM_POINT', 'CONFUSING', 'NONE') NOT NULL DEFAULT 'NONE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('USER', 'AI') NOT NULL,
    content TEXT NOT NULL,
    related_block_ids VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE beta_signups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_user_id ON documents(user_id);
CREATE INDEX idx_pdf_blocks_document_id ON pdf_blocks(document_id);
CREATE INDEX idx_explanations_document_id ON explanations(document_id);
CREATE INDEX idx_chat_messages_document_id ON chat_messages(document_id);
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
