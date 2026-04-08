-- chat_messages에 confidence ENUM 컬럼 추가
ALTER TABLE chat_messages ADD COLUMN confidence ENUM('HIGH','MEDIUM','LOW') NULL;
